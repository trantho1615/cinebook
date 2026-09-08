-- Sinh du lieu o quy mo that de EXPLAIN ANALYZE noi duoc dieu gi do.
--
-- Profile demo co ~477 suat chieu va 864 ghe. O kich thuoc do khong index nao kip co y
-- nghia: Postgres seq scan vai tram dong con nhanh hon di qua index. Muon biet truy van
-- nao xuong cap khi bang lon thi phai co bang lon that.
--
-- Chay:
--   docker exec -i cinebook-postgres psql -U cinebook -d cinebook \
--     -v st_per_room=22222 -f - < load-test/seed-large.sql
--
-- Thu nho truoc khi chay that:  -v st_per_room=100
--
-- Xoa du lieu da sinh:  load-test/seed-large-reset.sql

\set ON_ERROR_STOP on

-- Tham so. psql -v ghi de duoc; :'x' khong ton tai thi cac dong nay dat mac dinh.
\if :{?st_per_room} \else \set st_per_room 22222 \endif
\if :{?expired_per_st} \else \set expired_per_st 50 \endif
\if :{?booked_per_st} \else \set booked_per_st 10 \endif
\if :{?n_users} \else \set n_users 50000 \endif
\if :{?n_bookings} \else \set n_bookings 2000000 \endif
\if :{?n_outbox} \else \set n_outbox 2000000 \endif

\timing on
\echo '=== Tham so ==='
SELECT :st_per_room  AS suat_moi_phong,
       :expired_per_st AS hold_expired_moi_suat,
       :booked_per_st  AS hold_booked_moi_suat,
       :n_users        AS nguoi_dung,
       :n_bookings     AS don_hang,
       :n_outbox       AS outbox;

-- ---------------------------------------------------------------------------
-- 0. Bang tra cuu: ghe thu may cua moi phong
-- ---------------------------------------------------------------------------
-- seat_hold.seat_id CHI co rang buoc khoa ngoai toi seats, khong ep ghe phai thuoc phong
-- cua suat chieu. Nhung neu sinh sai phong thi truy van seat map se khong join duoc dong
-- nao, va toan bo bai do tro nen vo nghia — bang co 12 trieu dong ma duong nong khong
-- cham toi dong nao. Bang nay giu ghe dung phong.

DROP TABLE IF EXISTS seed_ref_room_seat;
CREATE TABLE seed_ref_room_seat AS
SELECT room_id,
       row_number() OVER (PARTITION BY room_id ORDER BY row_label, seat_number) AS ord,
       id AS seat_id
FROM seats;
CREATE UNIQUE INDEX ON seed_ref_room_seat (room_id, ord);

\echo '=== 0. Bang tra cuu ghe ==='
SELECT count(*) AS so_ghe, count(DISTINCT room_id) AS so_phong, max(ord) AS ghe_moi_phong
FROM seed_ref_room_seat;

-- ---------------------------------------------------------------------------
-- 1. Nguoi dung
-- ---------------------------------------------------------------------------
-- password_hash la mot chuoi bcrypt hop le nhung KHONG tuong ung mat khau nao dung duoc:
-- day la du lieu do tai, khong phai tai khoan dang nhap duoc.

DROP TABLE IF EXISTS seed_ref_users;
CREATE TABLE seed_ref_users AS
WITH ins AS (
    INSERT INTO users (id, email, password_hash, full_name, role, status, created_at, updated_at)
    SELECT gen_random_uuid(),
           'seed' || g || '@loadtest.invalid',
           '$2a$10$0000000000000000000000000000000000000000000000000000',
           'Seed User ' || g,
           'CUSTOMER', 'ACTIVE',
           now() - (g % 900) * interval '1 day',
           now()
    FROM generate_series(1, :n_users) g
    RETURNING id
)
SELECT id, row_number() OVER () AS rn FROM ins;
CREATE UNIQUE INDEX ON seed_ref_users (rn);

\echo '=== 1. Nguoi dung ==='
SELECT count(*) AS da_them FROM seed_ref_users;

-- ---------------------------------------------------------------------------
-- 2. Suat chieu
-- ---------------------------------------------------------------------------
-- Rang buoc kho nhat cua ca script: showtimes co
--   EXCLUDE USING gist (room_id WITH =, tstzrange(start_at, end_at) WITH &&)
--   WHERE (status = 'SCHEDULED')
-- nen KHONG duoc rai gio ngau nhien. Moi phong duoc cap mot day khe tuan tu, moi khe cach
-- nhau 3 gio va suat dai 2 gio, nen khong bao gio cham nhau.
--
-- Dat toan bo trong QUA KHU xa (bat dau 13 nam truoc) de khong dung vao ~477 suat cua
-- profile demo dang nam quanh hien tai, va de tim kiem suat chieu tuong lai cua ung dung
-- van tra ve dung nhung gi no von tra ve.

DROP TABLE IF EXISTS seed_ref_showtimes;
CREATE TABLE seed_ref_showtimes AS
WITH mv AS (SELECT array_agg(id ORDER BY id) AS a FROM movies),
     rm AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM rooms),
     ins AS (
    INSERT INTO showtimes (id, movie_id, room_id, start_at, end_at, base_price, status, created_at)
    SELECT gen_random_uuid(),
           (SELECT a FROM mv)[(i % array_length((SELECT a FROM mv), 1)) + 1],
           rm.id,
           now() - interval '13 years' + (i * interval '3 hours'),
           now() - interval '13 years' + (i * interval '3 hours') + interval '2 hours',
           70000 + (i % 5) * 10000,
           'SCHEDULED',
           now() - interval '13 years'
    FROM rm CROSS JOIN generate_series(0, :st_per_room - 1) i
    RETURNING id, room_id
)
SELECT id, room_id, row_number() OVER () AS rn FROM ins;
CREATE UNIQUE INDEX ON seed_ref_showtimes (rn);
CREATE INDEX ON seed_ref_showtimes (room_id);

\echo '=== 2. Suat chieu ==='
SELECT count(*) AS da_them, min(start_at)::date AS som_nhat, max(start_at)::date AS muon_nhat
FROM showtimes s JOIN seed_ref_showtimes r ON r.id = s.id;

-- ---------------------------------------------------------------------------
-- 3. Don hang
-- ---------------------------------------------------------------------------
-- code la varchar(12) UNIQUE: 'SD' + 10 chu so du cho 10 ty don.
-- 90% CONFIRMED, 10% EXPIRED — de idx_bookings_pending_expiry (partial WHERE PENDING)
-- van rong dung nhu thuc te, va idx_bookings_user co du lieu that de lam viec.

DROP TABLE IF EXISTS seed_ref_bookings;
CREATE TABLE seed_ref_bookings AS
WITH ins AS (
    INSERT INTO bookings (id, code, user_id, showtime_id, status, total_amount,
                          hold_expires_at, created_at, confirmed_at, cancelled_at, version)
    SELECT gen_random_uuid(),
           'SD' || lpad(g::text, 10, '0'),
           u.id,
           st.id,
           CASE WHEN g % 10 = 0 THEN 'EXPIRED' ELSE 'CONFIRMED' END,
           (1 + g % 4) * 90000,
           NULL,
           now() - (g % 3000) * interval '1 day',
           CASE WHEN g % 10 = 0 THEN NULL ELSE now() - (g % 3000) * interval '1 day' END,
           NULL,
           1
    FROM generate_series(1, :n_bookings) g
    JOIN seed_ref_users     u  ON u.rn  = (g % :n_users) + 1
    JOIN seed_ref_showtimes st ON st.rn = (g % (:st_per_room * (SELECT count(*) FROM rooms))) + 1
    RETURNING id
)
SELECT id, row_number() OVER () AS rn FROM ins;
CREATE UNIQUE INDEX ON seed_ref_bookings (rn);

\echo '=== 3. Don hang ==='
SELECT count(*) AS da_them FROM seed_ref_bookings;

-- ---------------------------------------------------------------------------
-- 4. seat_hold — bang lon nhat, va la bang duong nong
-- ---------------------------------------------------------------------------
-- Hai nhom, va su khac nhau giua chung la trong tam cua ca milestone:
--
--   EXPIRED  KHONG nam trong uk_seat_active (partial index chi chua HELD/BOOKED).
--            Day la phan "rac lich su" ma thiet ke von khang dinh la vo hai.
--
--   BOOKED   NAM trong uk_seat_active, va khong bao gio duoc don di. Nghia la index do
--            lon len theo tung ghe da ban, mai mai. Doan comment o V7__booking.sql noi
--            index nay "nho dung bang so ghe dang bi giu" — voi BOOKED thi khong dung.
--            Task 3 se do xem dieu do co thanh van de that khong.
--
-- BOOKED dung ghe thu 1..booked_per_st cua phong, moi (showtime, seat) dung MOT dong,
-- nen khong vi pham unique. EXPIRED dung ghe 1..expired_per_st va duoc trung thoai mai.

\echo '=== 4a. seat_hold EXPIRED ==='
INSERT INTO seat_hold (id, showtime_id, seat_id, booking_id, user_id, status,
                       expires_at, released_at, release_reason, created_at)
SELECT gen_random_uuid(),
       st.id,
       rs.seat_id,
       bk.id,
       u.id,
       'EXPIRED',
       now() - interval '13 years' + (st.rn * interval '3 hours'),
       now() - interval '13 years' + (st.rn * interval '3 hours') + interval '10 minutes',
       'SWEPT',
       now() - interval '13 years' + (st.rn * interval '3 hours') - interval '20 minutes'
FROM seed_ref_showtimes st
CROSS JOIN generate_series(1, :expired_per_st) g
JOIN seed_ref_room_seat rs ON rs.room_id = st.room_id AND rs.ord = g
JOIN seed_ref_bookings  bk ON bk.rn = ((st.rn * :expired_per_st + g) % :n_bookings) + 1
JOIN seed_ref_users     u  ON u.rn  = ((st.rn * :expired_per_st + g) % :n_users) + 1;

\echo '=== 4b. seat_hold BOOKED ==='
INSERT INTO seat_hold (id, showtime_id, seat_id, booking_id, user_id, status,
                       expires_at, released_at, release_reason, created_at)
SELECT gen_random_uuid(),
       st.id,
       rs.seat_id,
       bk.id,
       u.id,
       'BOOKED',
       NULL, NULL, NULL,
       now() - interval '13 years' + (st.rn * interval '3 hours') - interval '30 minutes'
FROM seed_ref_showtimes st
CROSS JOIN generate_series(1, :booked_per_st) g
JOIN seed_ref_room_seat rs ON rs.room_id = st.room_id AND rs.ord = g
JOIN seed_ref_bookings  bk ON bk.rn = ((st.rn * :booked_per_st + g) % :n_bookings) + 1
JOIN seed_ref_users     u  ON u.rn  = ((st.rn * :booked_per_st + g) % :n_users) + 1;

-- ---------------------------------------------------------------------------
-- 5. outbox_events
-- ---------------------------------------------------------------------------
-- idx_outbox_pending la partial WHERE published_at IS NULL. Rang buoc so 4 o spec noi
-- relay khong duoc scan toan bang. Sinh 99,9% dong DA publish de kiem tra dung dieu do:
-- bang to nhung index chi chua phan chua gui.

\echo '=== 5. outbox_events ==='
INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload,
                           created_at, published_at, attempt_count)
SELECT 'BOOKING',
       bk.id,
       CASE WHEN g % 3 = 0 THEN 'BookingConfirmed'
            WHEN g % 3 = 1 THEN 'BookingExpired'
            ELSE 'PaymentSucceeded' END,
       jsonb_build_object('bookingId', bk.id),
       now() - (g % 3000) * interval '1 day',
       CASE WHEN g % 1000 = 0 THEN NULL
            ELSE now() - (g % 3000) * interval '1 day' + interval '1 second' END,
       0
FROM generate_series(1, :n_outbox) g
JOIN seed_ref_bookings bk ON bk.rn = (g % :n_bookings) + 1;

-- ---------------------------------------------------------------------------
-- 6. ANALYZE — bat buoc, khong phai tuy chon
-- ---------------------------------------------------------------------------
-- Bo qua buoc nay thi planner van dung thong ke cua bang cu (vai tram dong) va se chon
-- ke hoach sai hoan toan. Moi so do sau do deu vo nghia.

\echo '=== 6. ANALYZE ==='
ANALYZE users;
ANALYZE showtimes;
ANALYZE bookings;
ANALYZE seat_hold;
ANALYZE outbox_events;

-- ---------------------------------------------------------------------------
-- 7. Kiem chung
-- ---------------------------------------------------------------------------

\echo '=== 7. So dong ==='
SELECT 'users' AS bang, count(*) FROM users
UNION ALL SELECT 'showtimes',     count(*) FROM showtimes
UNION ALL SELECT 'bookings',      count(*) FROM bookings
UNION ALL SELECT 'seat_hold',     count(*) FROM seat_hold
UNION ALL SELECT 'outbox_events', count(*) FROM outbox_events
ORDER BY 1;

\echo '=== 7b. seat_hold theo trang thai ==='
SELECT status, count(*) FROM seat_hold GROUP BY status ORDER BY 1;

\echo '=== 7c. Kich thuoc that tren dia ==='
SELECT relname AS bang,
       pg_size_pretty(pg_total_relation_size(c.oid))       AS tong,
       pg_size_pretty(pg_relation_size(c.oid))             AS du_lieu,
       pg_size_pretty(pg_indexes_size(c.oid))              AS index
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r'
  AND relname IN ('users','showtimes','bookings','seat_hold','outbox_events')
ORDER BY pg_total_relation_size(c.oid) DESC;

\echo '=== 7d. Kich thuoc tung index cua seat_hold ==='
SELECT indexrelname AS index, pg_size_pretty(pg_relation_size(indexrelid)) AS kich_thuoc
FROM pg_stat_user_indexes WHERE relname = 'seat_hold'
ORDER BY pg_relation_size(indexrelid) DESC;
