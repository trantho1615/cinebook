-- Xoa du lieu do tai do seed-large.sql sinh ra, giu nguyen du lieu cua profile demo.
--
--   docker exec -i cinebook-postgres psql -U cinebook -d cinebook -f - < load-test/seed-large-reset.sql
--
-- CHAM o quy mo that: xoa 12 trieu dong seat_hold mat vai phut. Neu khong can giu gi thi
-- `docker compose down -v` roi chay lai ung dung voi profile demo nhanh hon nhieu.
--
-- Cac bang seed_ref_* la ban do de tim lai dung nhung dong da sinh. Mat chung la khong con
-- cach nao phan biet du lieu do tai voi du lieu demo ngoai viec doan.

\set ON_ERROR_STOP on
\timing on

\echo '=== Truoc khi xoa ==='
SELECT 'seat_hold' AS bang, count(*) FROM seat_hold
UNION ALL SELECT 'bookings', count(*) FROM bookings
UNION ALL SELECT 'showtimes', count(*) FROM showtimes
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'outbox_events', count(*) FROM outbox_events
ORDER BY 1;

-- Thu tu xoa di theo chieu nguoc cua khoa ngoai.
\echo '=== outbox_events ==='
DELETE FROM outbox_events o USING seed_ref_bookings b WHERE o.aggregate_id = b.id;

\echo '=== seat_hold ==='
DELETE FROM seat_hold h USING seed_ref_showtimes s WHERE h.showtime_id = s.id;

\echo '=== booking_items ==='
DELETE FROM booking_items i USING seed_ref_bookings b WHERE i.booking_id = b.id;

\echo '=== bookings ==='
DELETE FROM bookings b USING seed_ref_bookings r WHERE b.id = r.id;

\echo '=== showtimes ==='
DELETE FROM showtimes s USING seed_ref_showtimes r WHERE s.id = r.id;

\echo '=== users ==='
DELETE FROM users u USING seed_ref_users r WHERE u.id = r.id;

DROP TABLE IF EXISTS seed_ref_room_seat;
DROP TABLE IF EXISTS seed_ref_users;
DROP TABLE IF EXISTS seed_ref_showtimes;
DROP TABLE IF EXISTS seed_ref_bookings;

-- Sau khi xoa nhieu, thong ke lech va bang van giu cho tren dia.
\echo '=== VACUUM ANALYZE ==='
VACUUM ANALYZE seat_hold;
VACUUM ANALYZE bookings;
VACUUM ANALYZE showtimes;
VACUUM ANALYZE users;
VACUUM ANALYZE outbox_events;

\echo '=== Sau khi xoa ==='
SELECT 'seat_hold' AS bang, count(*) FROM seat_hold
UNION ALL SELECT 'bookings', count(*) FROM bookings
UNION ALL SELECT 'showtimes', count(*) FROM showtimes
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'outbox_events', count(*) FROM outbox_events
ORDER BY 1;
