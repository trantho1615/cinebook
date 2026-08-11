# cinebook — Thiết kế Core Booking (Phase 1)

Hệ thống đặt vé xem phim.

- **Ngày:** 2026-08-11
- **Phạm vi:** Phase 1 — core booking engine. AI Agent hội thoại là Phase 2, có spec riêng.
- **Thời lượng dự kiến:** 8-10 tuần part-time (~10-15h/tuần)
- **Mục tiêu:** Dự án portfolio cho vị trí Software Engineer / Backend Developer (intern, fresher)

---

## 1. Mục tiêu và tiêu chí thành công

### 1.1 Mục tiêu

Xây dựng backend đặt vé xem phim giải quyết đúng những bài toán mà một hệ thống bán vé thật phải giải, thay vì một CRUD có thêm màn hình chọn ghế. Ba bài toán được chọn làm trọng tâm:

1. **Tranh chấp ghế đồng thời** — nhiều người cùng giành một ghế, cộng với cơ chế hết hạn giữ chỗ.
2. **Thanh toán bất đồng bộ** — idempotency, transactional outbox, webhook đến trùng / đến muộn / không đến.
3. **Observability có số liệu** — metric nghiệp vụ, load test có bằng chứng trước/sau.

### 1.2 Tiêu chí thành công

Dự án được coi là hoàn thành khi:

- Bắn 500 VU vào một suất chiếu 150 ghế trong 60 giây, sau đó truy vấn kiểm tra bất biến trả về **0 dòng** (không ghế nào bị đặt hai lần).
- Sáu integration test cốt lõi (mục 8.2) chạy xanh trong CI trên Postgres/Redis/Kafka thật.
- README có biểu đồ p50/p95/p99 của endpoint seat-map **trước và sau** khi thêm Redis cache, đo bằng k6.
- Có video demo hai tab trình duyệt tranh nhau một ghế, ghế đổi màu realtime.
- Toàn bộ luồng đặt vé chạy được end-to-end: chọn ghế → thanh toán → nhận vé → hoặc hết hạn tự nhả ghế.

### 1.3 Ngoài phạm vi (cố ý không làm)

Những thứ sau bị loại bỏ có chủ đích. Chúng không thêm chiều sâu kỹ thuật tương xứng với thời gian bỏ ra:

| Không làm | Lý do |
|---|---|
| API Gateway, service discovery | Chỉ có 2 deployable — thêm vào là hạ tầng thừa |
| Elasticsearch | Postgres full-text search đủ cho quy mô này |
| Xác thực email, quên mật khẩu, OAuth Google, 2FA | Không thêm bài toán kỹ thuật nào cho hệ đặt vé |
| Virtual waiting room, anti-bot | Đã cân nhắc và loại — dễ thành đồ trang trí |
| Combo bắp nước, voucher, tích điểm | Là bài toán nghiệp vụ, không phải bài toán hệ thống |
| Trang admin đầy đủ | Dữ liệu quản trị nạp bằng seed script và Flyway |
| Microservices đầy đủ | Phần lớn effort sẽ đổ vào infra glue thay vì nghiệp vụ |

---

## 2. Kiến trúc tổng thể

### 2.1 Hình dạng hệ thống

```
┌─────────────┐   REST + WebSocket
│  web (UI)   │──────────────┐
│ seat map    │              ▼
└─────────────┘      ┌──────────────────────────────┐
                     │   cinebook-api (monolith)    │
   cổng thanh toán   │  identity │ catalog │ booking │
   ──── webhook ────▶│  payment  │ notification      │
                     └───┬──────────┬────────┬───────┘
                         │          │        │
                  Postgres 16   Redis 7   Kafka (outbox relay)
                    (SoT)      (cache+   ──────┬──────
                               pub/sub)        ▼
                                      ┌──────────────────┐
                                      │ cinebook-worker  │
                                      │ • hold sweeper   │
                                      │ • email/notify   │
                                      │ • payment recon  │
                                      │ • outbox relay   │
                                      └──────────────────┘
```

Hai deployable:

- **`cinebook-api`** — phục vụ mọi request đồng bộ (REST + WebSocket). Không chạy scheduled job nào.
- **`cinebook-worker`** — không nhận HTTP request nào. Chỉ tiêu thụ Kafka và chạy scheduled job.

Ranh giới này có ý nghĩa vận hành cụ thể: **worker chết thì hệ thống vẫn bán được vé**, chỉ mất tính kịp thời của việc dọn dẹp và gửi thông báo. Tính đúng đắn không phụ thuộc vào worker (xem mục 6.2).

### 2.2 Cấu trúc Maven

```
cinebook/
├── pom.xml                 parent, quản lý version
├── common/                 domain event contract, shared DTO
├── cinebook-api/           monolith module hoá
├── cinebook-worker/        Kafka consumer + scheduled job
├── web/                    UI tối giản
├── load-test/              kịch bản k6
└── docker-compose.yml      postgres, redis, kafka, prometheus, grafana, jaeger
```

Toạ độ Maven: `groupId` = `com.cinebook`, `artifactId` của parent = `cinebook`. Base package = `com.cinebook`.

### 2.3 Tech stack

| Vai trò | Lựa chọn | Lý do |
|---|---|---|
| Runtime | Java 21 + Spring Boot 3.5 | Đã có sẵn; virtual thread, record, pattern matching |
| Build | Maven 3.9 multi-module | Đã có sẵn |
| Database | PostgreSQL 16 + Flyway | Partial unique index và exclusion constraint là mấu chốt thiết kế |
| Truy cập dữ liệu | Spring Data JPA + `NamedParameterJdbcTemplate` | JPA cho CRUD thường; **JDBC thuần cho luồng giữ ghế, webhook, relay, sweeper** — xem mục 6.2.1 |
| Cache / pub-sub | Redis 7 | Cache seat-map, đẩy realtime qua WebSocket |
| Messaging | Kafka (KRaft, không Zookeeper) | Outbox relay, event bất đồng bộ |
| Auth | Spring Security + JWT tự implement | Xem mục 4 |
| Test | JUnit 5 + Testcontainers + Awaitility + AssertJ | Test trên hạ tầng thật, không H2 |
| Kiến trúc | ArchUnit | Ép ranh giới module ở tầng build |
| Observability | Micrometer → Prometheus + Grafana; OpenTelemetry → Jaeger; log JSON có traceId | Metric nghiệp vụ, không chỉ metric máy |
| Load test | k6 | Kịch bản flash sale, xuất biểu đồ |
| CI | GitHub Actions | Build + test mỗi push |
| Frontend | React (hoặc HTMX) tối giản | Chỉ seat map realtime + trang thanh toán |

---

## 3. Module boundary

### 3.1 Năm module

```
identity  │  catalog  │  booking  │  payment  │  notification
```

| Module | Trách nhiệm |
|---|---|
| `identity` | User, đăng nhập, token, RBAC |
| `catalog` | Phim, rạp, phòng chiếu, sơ đồ ghế, suất chiếu, bảng giá |
| `booking` | Giữ ghế, đơn đặt, vé — **trái tim hệ thống** |
| `payment` | Giao dịch, webhook, hoàn tiền, đối soát |
| `notification` | Email / thông báo, chỉ tiêu thụ event |

**Quyết định gộp `catalog` + `scheduling`:** ban đầu tách riêng, sau đó gộp lại. Lý do quyết định: mọi truy vấn suất chiếu đều kéo theo phim và phòng chiếu, nên nếu tách thì `booking` → `scheduling` → `catalog` tạo thành chuỗi phụ thuộc mà tầng giữa chỉ làm nhiệm vụ chuyển tiếp lời gọi và viết DTO mapper. Một module `api` chỉ tồn tại để forward là dấu hiệu ranh giới vẽ sai chỗ. Lợi ích kèm theo: đường đọc seat-map nằm gọn trong một module, thành một query duy nhất thay vì ba lần round-trip — chính là endpoint sẽ được load test và cache.

### 3.2 Cấu trúc bên trong mỗi module

Mọi module nằm dưới base package `com.cinebook`:

```
com.cinebook.booking/
├── api/      interface + DTO — DUY NHẤT phần module khác nhìn thấy
├── domain/   entity, business rule, không phụ thuộc Spring Web
├── infra/    JPA repository, Redis, Kafka producer
└── web/      REST controller, request/response model
```

Riêng `catalog` chia sub-package theo aggregate để không phình thành nơi chứa mọi thứ:

```
com.cinebook.catalog/
├── api/       MovieQuery │ VenueQuery │ ShowtimeQuery │ PriceQuery
├── domain/
│   ├── movie/     Movie, Genre
│   ├── venue/     Cinema, Room, Seat
│   └── showtime/  Showtime, PriceRule
├── infra/
└── web/
```

### 3.3 Luật phụ thuộc

- Module X chỉ được import `Y.api`. Cấm import `Y.domain` và `Y.infra`.
- Interface trong `api` phải hẹp và tách bạch, không dồn vào một `CatalogService` khổng lồ. `booking` chỉ import `ShowtimeQuery` và `PriceQuery`.
- `catalog` không bao giờ import `booking`. Quan hệ một chiều: `catalog` là bên cung cấp dữ liệu đọc.
- Đường nóng dùng đúng một DTO hợp thành: `ShowtimeDetail` (suất chiếu + tóm tắt phim + phòng + sơ đồ ghế) trả về bởi một lời gọi duy nhất. Đây là read model được cache ở Redis.

Luật này được kiểm chứng bằng **ArchUnit test** — build fail khi vi phạm, không dựa vào kỷ luật cá nhân.

### 3.4 Ghi chú về `PriceQuery`

`PriceRule` nằm trong `catalog` ở phase 1 (giá cơ bản theo suất chiếu + phụ phí theo loại ghế). Nếu về sau thêm voucher, combo, giá theo hạng thành viên thì tách `pricing` thành module riêng. Giữ `PriceQuery` là interface độc lập ngay từ đầu để việc tách sau này chỉ tốn một buổi.

---

## 4. Xác thực và phân quyền

### 4.1 Quyết định

Spring Security + JWT **tự implement**, không dùng Keycloak. Lý do: Keycloak biến auth thành bài cấu hình — nhanh hơn khoảng 2 ngày nhưng không để lại bài toán nào để trình bày. Tự implement cho ba bài toán thật: refresh token rotation kèm phát hiện tái sử dụng, object-level authorization, và đánh đổi stateless/stateful.

Không tự viết thuật toán ký — dùng `jjwt` hoặc Nimbus.

### 4.2 Thành phần

| Thành phần | Trách nhiệm |
|---|---|
| `SecurityConfig` | Khai báo endpoint public / cần đăng nhập / cần role ADMIN |
| `JwtAuthenticationFilter` | Verify token, nạp identity vào `SecurityContext` |
| `TokenService` | Phát access token (15 phút) + refresh token (7 ngày) |
| `RefreshTokenStore` | Lưu refresh token đã hash vào Redis, xoay vòng khi dùng |
| `AuthController` | register / login / refresh / logout |

### 4.3 Refresh token rotation và phát hiện tái sử dụng

Mỗi refresh token thuộc về một **token family**. Khi dùng để refresh: token cũ bị đánh dấu đã dùng, token mới cùng family được phát ra. Nếu một token **đã dùng** bị dùng lại lần nữa → dấu hiệu bị đánh cắp → huỷ toàn bộ family, buộc đăng nhập lại.

Lưu trong Redis:
- `rt:{jti}` → `{userId, familyId, used}`, TTL 7 ngày
- `rtfam:{familyId}` → tập các jti thuộc family, để huỷ hàng loạt

### 4.4 Object-level authorization

`GET /bookings/{id}` phải chặn user A xem booking của user B. Kiểm tra quyền sở hữu ở tầng use-case, không chỉ ở controller. Có integration test riêng cho lỗ hổng IDOR này.

### 4.5 Role

`CUSTOMER` (mặc định), `STAFF` (quét vé), `ADMIN` (quản trị catalog).

### 4.6 Chặn dò mật khẩu

Endpoint `POST /auth/login` giới hạn **5 lần sai / 15 phút** theo cặp (IP, email), đếm bằng counter Redis có TTL. Vượt ngưỡng trả 429. Đây là ràng buộc rate limit **duy nhất** trong phase 1 — rate limit toàn hệ thống và chống bot mua vé nằm ngoài phạm vi (mục 1.3).

---

## 5. Data model

### 5.1 Quy ước chung

- Khoá chính: `UUID` (`gen_random_uuid()`), trừ bảng log dùng `bigserial`.
- Thời gian: `timestamptz`, lưu UTC, hiển thị theo `Asia/Ho_Chi_Minh`.
- Tiền: `BIGINT`, đơn vị **đồng** (VND không có phần lẻ). Không dùng `float`.
- Mọi bảng nghiệp vụ có `created_at`; bảng có cập nhật thì thêm `updated_at`.

### 5.2 `identity`

```
users(id, email UNIQUE, password_hash, full_name, phone,
      role, status, created_at, updated_at)
```

Refresh token nằm ở Redis (mục 4.3), không có bảng.

### 5.3 `catalog`

```
movies(id, title, original_title, description, duration_min,
       genres text[], age_rating, poster_url, trailer_url,
       release_date, status, created_at)

cinemas(id, name, address, district, city, latitude, longitude, status)

rooms(id, cinema_id → cinemas, name, room_type, status)
      UNIQUE (cinema_id, name)

seats(id, room_id → rooms, row_label, seat_number, seat_type, is_active)
      UNIQUE (room_id, row_label, seat_number)
      seat_type: STANDARD | VIP | COUPLE

showtimes(id, movie_id → movies, room_id → rooms,
          start_at, end_at, base_price bigint, status, created_at)

price_rules(seat_type PK, surcharge bigint)
```

Giá một ghế = `showtimes.base_price + price_rules.surcharge` theo `seat_type`, chốt lại tại thời điểm giữ ghế và lưu vào `booking_items.unit_price`. Bảng `price_rules` cố ý chỉ là bảng tra cứu phẳng — giá theo khung giờ, ngày lễ, hạng thành viên đều nằm ngoài phạm vi phase 1.

### 5.4 `booking`

```
bookings(id, code UNIQUE, user_id, showtime_id, status,
         total_amount bigint, hold_expires_at,
         created_at, confirmed_at, cancelled_at, version int)
         status: PENDING | CONFIRMED | CANCELLED | EXPIRED | REFUNDED

seat_hold(id, showtime_id, seat_id, booking_id, user_id, status,
          expires_at NULL, released_at NULL, release_reason NULL, created_at)
          status: HELD | BOOKED | EXPIRED
          release_reason: TAKEN_OVER | SWEPT | USER_CANCELLED | PAYMENT_FAILED

booking_items(id, booking_id → bookings, seat_id, seat_label,
              unit_price bigint, ticket_code)
              UNIQUE (booking_id, seat_id)

idempotency_keys(key PK, user_id, endpoint, request_hash,
                 response_status, response_body jsonb,
                 created_at, expires_at)

audit_log(id bigserial, aggregate_type, aggregate_id, action,
          actor_type, actor_id, payload jsonb, created_at)
          actor_type: USER | AGENT | SYSTEM
```

`seat_hold` là bảng **thưa** — chỉ chứa ghế đã từng bị giữ, không materialize toàn bộ ghế cho mỗi suất chiếu. Seat map = `seats LEFT JOIN seat_hold`.

`expires_at` được set `NULL` khi hold chuyển sang `BOOKED` (vé đã thanh toán thì không còn khái niệm hết hạn). `released_at` và `release_reason` chỉ có giá trị khi `status = 'EXPIRED'`.

`bookings.version` là cột optimistic lock của JPA (`@Version`), bảo vệ các thao tác cập nhật booking (confirm, cancel, refund) khỏi ghi đè lẫn nhau. Riêng việc giữ ghế không dựa vào cột này mà dựa vào unique partial index ở mục 5.7.

`ticket_code` được sinh khi booking chuyển sang `CONFIRMED`, không cần bảng `tickets` riêng.

`audit_log.actor_type` có mặt ngay từ phase 1 dù phase 1 chưa có agent — thêm một cột bây giờ gần như miễn phí, còn thêm sau thì phải backfill.

### 5.5 `payment`

```
payments(id, booking_id, provider, provider_txn_id, amount bigint,
         status, idempotency_key UNIQUE, created_at, updated_at)
         status: INITIATED | SUCCEEDED | FAILED | REFUNDED

payment_events(id bigserial, payment_id, provider,
               provider_event_id UNIQUE, raw_payload jsonb,
               signature_valid bool, received_at, processed_at)

refunds(id, payment_id, amount bigint, reason, status,
        provider_refund_id, created_at)
```

`payment_events` lưu **payload thô** của mọi webhook nhận được, kể cả cái bị từ chối vì sai chữ ký. Đây là thứ cứu bạn khi phải điều tra sự cố thanh toán.

### 5.6 Chung

```
outbox_events(id bigserial, aggregate_type, aggregate_id, event_type,
              payload jsonb, created_at, published_at,
              attempt_count, last_error)

notifications(id, user_id, channel, template, payload jsonb,
              status, sent_at, attempt_count)
```

### 5.7 Bốn ràng buộc làm nên tính đúng đắn

```sql
-- 1. Không bao giờ hai người giữ cùng một ghế của cùng một suất chiếu
CREATE UNIQUE INDEX uk_seat_active ON seat_hold (showtime_id, seat_id)
  WHERE status IN ('HELD', 'BOOKED');

-- 2. Không xếp hai suất chiếu chồng giờ trong cùng một phòng
ALTER TABLE showtimes ADD CONSTRAINT no_overlap
  EXCLUDE USING gist (room_id WITH =, tstzrange(start_at, end_at) WITH &&);

-- 3. Không bao giờ charge hai lần cho cùng một yêu cầu
ALTER TABLE payments ADD CONSTRAINT uk_idem UNIQUE (idempotency_key);

-- 4. Relay quét outbox không scan toàn bảng
CREATE INDEX idx_outbox_pending ON outbox_events (created_at)
  WHERE published_at IS NULL;
```

Nguyên tắc xuyên suốt: **tính đúng đắn được bảo vệ ở tầng thấp nhất có thể**. Code ứng dụng có thể có bug, có race, có nhiều instance chạy song song — database vẫn không cho phép trạng thái sai tồn tại.

Ràng buộc số 1 có một tính chất đáng chú ý: vì là partial index, nó chỉ chứa row `HELD`/`BOOKED`. Dù `seat_hold` tích luỹ hàng trăm nghìn row `EXPIRED`, index quyết định hiệu năng đường nóng vẫn nhỏ đúng bằng số ghế đang thực sự bị giữ.

---

## 6. Luồng nghiệp vụ

### 6.1 State machine của booking

```
   POST /holds
        │
        ▼
  ┌───────────┐  quá 10 phút          ┌───────────┐
  │  PENDING  │──────────────────────▶│  EXPIRED  │  ─┐
  └─────┬─────┘  user huỷ ────────┐   └───────────┘   │
        │                         ▼                   │  giải phóng ghế
        │ webhook TT thành công ┌───────────┐         │  (seat_hold → EXPIRED)
        ▼                       │ CANCELLED │  ───────┤
  ┌───────────┐                 └───────────┘         │
  │ CONFIRMED │                                       │
  └─────┬─────┘                                       │
        │ refund trước giờ chiếu ┌───────────┐        │
        └───────────────────────▶│ REFUNDED  │  ──────┘
                                 └───────────┘
```

Bốn trạng thái kết thúc: `EXPIRED`, `CANCELLED`, `REFUNDED` đều giải phóng ghế; `CONFIRMED` là trạng thái ổn định của vé hợp lệ.

Chỉ tiến, không lùi. Ràng buộc nằm trong domain: `booking.confirm()` ném exception nếu state khác `PENDING`; `booking.refund()` ném exception nếu state khác `CONFIRMED`. Có unit test cho mọi chuyển đổi hợp lệ và bất hợp lệ.

TTL giữ chỗ: **10 phút**, cấu hình được qua `booking.hold.ttl`.

### 6.2 Bước 1 — Giữ ghế

`POST /showtimes/{id}/holds` kèm header `Idempotency-Key`.

Quy tắc nghiệp vụ: tối đa **8 ghế** một lần giữ (vượt → 422). Suất chiếu phải còn ở trạng thái `SCHEDULED` và chưa tới `start_at`.

```sql
BEGIN;
  -- (a) người đến sau giải phóng hold đã hết hạn, nhưng GIỮ LẠI lịch sử
  UPDATE seat_hold
     SET status = 'EXPIRED', released_at = now(), release_reason = 'TAKEN_OVER'
   WHERE showtime_id = ? AND seat_id IN (?, ?, ?)
     AND status = 'HELD' AND expires_at <= now();

  -- (b) tạo booking TRƯỚC, vì seat_hold.booking_id tham chiếu tới nó
  INSERT INTO bookings (id, code, user_id, showtime_id, status,
                        hold_expires_at, total_amount)
  VALUES (?, ?, ?, ?, 'PENDING', now() + interval '10 min', ?);

  -- (c) giữ TẤT CẢ ghế trong một câu, all-or-nothing
  INSERT INTO seat_hold (showtime_id, seat_id, booking_id, user_id, status, expires_at)
  SELECT ?, s.seat_id, ?, ?, 'HELD', now() + interval '10 min'
    FROM unnest(?::uuid[]) AS s(seat_id)
   ORDER BY s.seat_id                       -- SẮP XẾP TĂNG DẦN: chống deadlock
      ON CONFLICT (showtime_id, seat_id) WHERE status IN ('HELD','BOOKED') DO NOTHING
   RETURNING seat_id;                       -- trả về ĐÚNG những ghế giữ được

  -- (d) chốt giá tại thời điểm giữ ghế
  INSERT INTO booking_items (booking_id, seat_id, seat_label, unit_price)
  VALUES (...), (...), (...);

  INSERT INTO outbox_events (event_type, payload) VALUES ('SeatsHeld', ...);
COMMIT;
```

`bookings.id` và `bookings.code` được sinh ở tầng ứng dụng trước khi mở transaction, để câu (c) có sẵn `booking_id`.

Ba quyết định trong đoạn này:

**1. Câu (a) dùng UPDATE chứ không DELETE.** Unique partial index chặn theo `status IN ('HELD','BOOKED')` — nó không biết `expires_at`, nên một hold đã hết hạn vẫn chiếm chỗ trong index và phải được chuyển trạng thái. Dùng `UPDATE` thay vì `DELETE` để: (1) giữ lịch sử phục vụ phân tích funnel, (2) đường lazy và đường sweeper hội tụ về **cùng một phép chuyển trạng thái**, chỉ khác `release_reason`. Row chuyển sang `EXPIRED` tự động rơi khỏi partial index nên ghế trống ngay lập tức — không cần xoá row để giải phóng ghế.

**2. Câu (c) sắp xếp `seat_id` tăng dần trước khi insert.** Hai người cùng giữ {F7, F8} theo thứ tự ngược nhau sẽ deadlock: mỗi transaction giữ một row và chờ row kia. Sắp xếp trước khi insert khiến mọi transaction khoá theo cùng thứ tự. Bug này chỉ xuất hiện dưới tải.

**3. Phát hiện xung đột bằng `RETURNING`, không bằng exception.** `ON CONFLICT DO NOTHING` khiến câu lệnh không ném lỗi; `RETURNING seat_id` trả về đúng những ghế giữ được. Ứng dụng so số dòng trả về với số ghế yêu cầu — thiếu dòng nào thì ghế đó đã có chủ → rollback → trả **409 Conflict** kèm danh sách chính xác → UI tô đỏ đúng ghế đó.

Cách này tránh được hai vấn đề của việc bắt `DataIntegrityViolationException`: không phải điều khiển luồng bằng exception, và không phải parse chuỗi `detail` của `PSQLException` để biết ghế nào xung đột.

`ON CONFLICT DO NOTHING` khi gặp row đang bị transaction khác giữ chưa commit thì **chờ** transaction kia kết thúc rồi mới quyết định: bên kia commit thì bỏ qua ghế đó, bên kia rollback thì chèn được. Đúng hành vi mong muốn, không cần retry ở tầng ứng dụng.

Giữ nhiều ghế là all-or-nothing: không có chuyện giữ được 2 trong 3 ghế.

### 6.2.1 Ghi chú thi công: luồng này KHÔNG dùng JPA entity

Hibernate `ActionQueue` sắp xếp lại câu lệnh theo **loại thao tác** chứ không theo thứ tự gọi trong code. Thứ tự flush luôn là: mọi `INSERT` → mọi `UPDATE` → mọi `DELETE`.

Áp vào transaction ở mục 6.2: bước (a) là `UPDATE`, các bước (b)(c)(d) là `INSERT`. Nếu viết bằng `repository.save()`, Hibernate sẽ chạy các `INSERT` **trước** câu `UPDATE` dọn hold hết hạn. Hậu quả: `INSERT seat_hold` đâm vào row `HELD` đã quá hạn nhưng chưa được chuyển trạng thái → xung đột → trả 409 cho ghế thực ra đang trống. **Toàn bộ cơ chế lazy expiration ở mục 6.6 sẽ chết lặng lẽ** — không crash, không log lỗi, chỉ là ghế hết hạn không bao giờ được người sau lấy lại.

Lý do thứ hai: JPA hoãn flush tới lúc commit, nên vi phạm ràng buộc nổi lên **sau khi method use-case đã return**, nằm ngoài khối xử lý lỗi.

**Quy tắc:**

| Dùng `NamedParameterJdbcTemplate` | Dùng JPA |
|---|---|
| `HoldSeatsUseCase` | Catalog CRUD (phim, rạp, phòng, ghế) |
| Webhook confirm (mục 6.4) | Đọc seat map / read model |
| Outbox relay (mục 6.5) | Lịch sử booking của user |
| Sweeper (mục 6.6) | `identity` |

**Không trộn hai thứ trong cùng một transaction.** `JdbcTemplate` không thấy thay đổi entity đang chờ flush trong Hibernate session, và session không biết gì về những gì JDBC vừa ghi — trộn vào là có dữ liệu cũ trong first-level cache.

Tính nguyên tử vẫn được đảm bảo: `JdbcTemplate` lấy connection qua `DataSourceUtils` nên chạy trong đúng transaction mà `JpaTransactionManager` đang quản lý.

`ExpiredHoldTakeoverTest` (mục 8.2) chính là test bắt được lỗi này — nếu về sau ai đó refactor luồng giữ ghế sang `repository.save()`, test đó sẽ đỏ.

### 6.3 Bước 2 — Khởi tạo thanh toán

`POST /bookings/{id}/payments`

1. Kiểm tra booking thuộc về user gọi, và đang ở `PENDING`, và `hold_expires_at > now()`.
2. Tạo row `payments` với `idempotency_key` = `booking_id + ':' + attempt`.
3. Gọi provider, trả về redirect URL.

### 6.4 Bước 3 — Webhook thanh toán

```
POST /webhooks/payment
  (1) verify chữ ký HMAC — sai thì ghi payment_events với signature_valid=false, trả 401
  (2) INSERT payment_events (provider_event_id UNIQUE)
      └─ trùng? trả 200 ngay, KHÔNG xử lý lại
  (3) BEGIN;
        payments   → SUCCEEDED
        bookings   → CONFIRMED (sinh ticket_code cho từng booking_item)
        seat_hold  → BOOKED, xoá expires_at
        outbox     ← 'BookingConfirmed'
      COMMIT;
  (4) trả 200
```

Webhook **luôn** phải idempotent vì mọi cổng thanh toán đều retry. Dedupe bằng `provider_event_id UNIQUE` là cách rẻ nhất và chắc nhất.

Bước (3) cập nhật `seat_hold` với điều kiện `WHERE status = 'HELD'`. Nếu hold đã hết hạn và ghế đã có chủ mới, câu update ảnh hưởng 0 row → transaction abort → rơi vào nhánh xử lý ở mục 7.

### 6.5 Bước 4 — Outbox relay

Chạy trong `cinebook-worker`, chu kỳ 1 giây:

```sql
SELECT * FROM outbox_events
 WHERE published_at IS NULL
 ORDER BY created_at LIMIT 100
 FOR UPDATE SKIP LOCKED;
```

publish lên Kafka → `UPDATE published_at = now()`.

**Vì sao cần outbox:** nếu `COMMIT` database rồi mới `kafkaTemplate.send()`, tiến trình chết giữa hai lệnh là mất event vĩnh viễn — vé đã đặt nhưng email không bao giờ gửi. Outbox biến việc ghi event thành một phần của chính transaction nghiệp vụ.

**Đánh đổi:** giao hàng at-least-once, nên mọi consumer phải idempotent.

`FOR UPDATE SKIP LOCKED` cho phép chạy nhiều instance relay song song mà không cần leader election.

Topic Kafka: `booking.events`, `payment.events`. Event type: `SeatsHeld`, `BookingConfirmed`, `BookingExpired`, `PaymentSucceeded`, `PaymentFailed`, `RefundIssued`.

### 6.6 Bước 5 — Hai lớp xử lý hết hạn

| Lớp | Cơ chế | Đảm bảo |
|---|---|---|
| **Lazy** | `UPDATE` hold hết hạn ngay trong transaction giữ ghế (mục 6.2a) | **Đúng** — không bao giờ có ghế bị khoá oan |
| **Sweeper** | Job 30 giây/lần trong worker + ShedLock, phát event | **Kịp thời** — UI người khác thấy ghế trống ra mà không cần ai thử giữ |

Sweeper làm hai việc:

```sql
UPDATE seat_hold SET status='EXPIRED', released_at=now(), release_reason='SWEPT'
 WHERE status='HELD' AND expires_at <= now();

UPDATE bookings SET status='EXPIRED'
 WHERE status='PENDING' AND hold_expires_at <= now();
```

ShedLock đảm bảo chỉ một instance chạy tại một thời điểm.

**Quyết định có chủ ý:** đường lazy (mục 6.2a) giải phóng ghế của người khác nhưng **không đụng vào row `bookings` của họ** — sửa booking của người khác trong transaction của mình mở thêm một hướng deadlock mới. Booking đó nằm `PENDING` cho tới khi sweeper dọn, và trong lúc đó nó không thể confirm được vì bước confirm yêu cầu `seat_hold.status = 'HELD'`. Tính đúng đắn không phụ thuộc sweeper; sweeper chỉ dọn dẹp cho gọn.

### 6.7 Bước 6 — Realtime tới UI

Sau khi commit, publish Redis pub/sub → mọi instance `cinebook-api` đang giữ WebSocket đẩy xuống `/topic/showtimes/{id}` → UI cập nhật màu ghế.

Phân vai rõ ràng: **Redis pub/sub** cho việc nhẹ và cần ngay (cập nhật màu ghế). **Kafka** cho việc nặng và chậm (email, đối soát, analytics).

### 6.8 Job lưu trữ

Hàng tháng, chuyển row `seat_hold` trạng thái `EXPIRED` cũ hơn 90 ngày sang bảng archive. Task nhỏ, xếp cuối lộ trình.

---

## 7. Xử lý sự cố

| Tình huống | Cách xử lý |
|---|---|
| Hai người giành một ghế | Unique index chặn → 409 kèm danh sách ghế xung đột |
| Deadlock khi giữ nhiều ghế | Sắp xếp `seat_id` trước khi insert |
| Client bấm hai lần / mạng chập chờn | `Idempotency-Key` → trả lại nguyên kết quả lần đầu |
| Webhook đến hai lần | Dedupe theo `provider_event_id` |
| Webhook sai chữ ký | Ghi `payment_events` với `signature_valid=false`, trả 401, không xử lý |
| **Webhook không bao giờ đến** | Job đối soát trong worker: quét `payments` ở `INITIATED` quá 15 phút → chủ động gọi API tra cứu trạng thái bên cổng thanh toán |
| **Webhook đến sau khi hold đã hết hạn** | Câu update `seat_hold` ảnh hưởng 0 row → abort → tạo `refunds` tự động + gửi email xin lỗi |
| Redis chết | Seat map fallback đọc thẳng Postgres — chậm hơn nhưng vẫn bán được vé |
| Kafka chết | Event nằm lại trong outbox, relay tự retry với `attempt_count` và `last_error` |
| Worker chết | Lazy expiration vẫn giữ tính đúng; chỉ mất realtime và email |
| Relay gửi trùng | At-least-once — consumer idempotent theo `outbox_events.id` |

Quy ước mã lỗi HTTP:

| Mã | Khi nào |
|---|---|
| 400 | Payload sai định dạng |
| 401 | Thiếu / sai token, sai chữ ký webhook |
| 403 | Có token nhưng không đủ quyền, hoặc truy cập tài nguyên của người khác |
| 404 | Không tồn tại |
| 409 | Ghế đã bị người khác giữ; booking sai trạng thái |
| 410 | Hold đã hết hạn |
| 422 | Vi phạm quy tắc nghiệp vụ (ví dụ đặt quá 8 ghế một lần) |
| 429 | Vượt rate limit |

---

## 8. Chiến lược test

### 8.1 Nguyên tắc

Integration test chạy trên **Postgres/Redis/Kafka thật qua Testcontainers**, không dùng H2. Lý do trực tiếp: H2 không có partial index, không có `EXCLUDE USING gist`, không có `FOR UPDATE SKIP LOCKED` — toàn bộ phần cốt lõi của thiết kế này sẽ không được test nếu dùng H2.

| Tầng | Công cụ | Phạm vi |
|---|---|---|
| Unit | JUnit 5 + AssertJ, không Spring context | State machine, tính giá, quy tắc giữ ghế |
| Integration | Testcontainers | Repository, transaction, ràng buộc DB |
| API | MockMvc / RestAssured | Contract, mã lỗi, phân quyền |
| E2E | Testcontainers + Awaitility | Hold → pay → confirm qua cả hai deployable |
| Load | k6 | Kịch bản flash sale |

### 8.2 Sáu test cốt lõi

| Test | Chứng minh |
|---|---|
| `ConcurrentSeatHoldTest` — 200 thread + `CountDownLatch` cùng giành 1 ghế | Đúng 1 thành công, 199 nhận 409 |
| `DeadlockRegressionTest` — hai nhóm giữ {F7,F8} và {F8,F7} | Thứ tự khoá đúng, không deadlock |
| `WebhookIdempotencyTest` — gửi cùng webhook 5 lần | Chỉ 1 booking confirmed, không double-charge |
| `OutboxRelayTest` — kill relay giữa chừng rồi bật lại | Không mất event |
| `ExpiredHoldTakeoverTest` | Ghế hết hạn được người sau lấy đúng, lịch sử vẫn còn |
| `LateWebhookTest` | Webhook đến muộn → tự động refund, không confirm sai |

### 8.3 Kiểm tra bất biến sau load test

```sql
-- phải trả về 0 dòng
SELECT showtime_id, seat_id, count(*)
  FROM seat_hold WHERE status = 'BOOKED'
 GROUP BY 1, 2 HAVING count(*) > 1;
```

---

## 9. Observability

### 9.1 Ba trụ

- **Log:** JSON, có `traceId` trong MDC, xuất ra stdout.
- **Metric:** Micrometer → Prometheus → Grafana.
- **Trace:** OpenTelemetry → Jaeger.

### 9.2 Metric nghiệp vụ

Dashboard duy nhất tên **Booking Health**, tập trung vào metric nghiệp vụ chứ không chỉ CPU/memory:

```
seat_hold_conflict_total        số lần hai người giành một ghế
booking_conversion_rate         tỉ lệ HELD → BOOKED, tính từ release_reason
hold_to_payment_seconds         histogram — TTL 10 phút có hợp lý không
outbox_lag_seconds              event nằm chờ bao lâu — sức khoẻ đường async
payment_webhook_late_total      webhook đến sau khi hold hết hạn
```

Một alert rule duy nhất: `outbox_lag_seconds > 60`.

### 9.3 Load test

Kịch bản **flash sale**: 500 VU cùng lao vào một suất chiếu 150 ghế trong 60 giây.

Đo: p50/p95/p99 endpoint seat-map, tỉ lệ 409, throughput hold/giây, mức bão hoà connection pool.

Chạy hai lần — **trước và sau khi thêm Redis cache cho seat-map** — và đặt hai biểu đồ cạnh nhau trong README. Kết thúc mỗi lần chạy thì chạy truy vấn bất biến ở mục 8.3.

---

## 10. Lộ trình

| Tuần | Milestone | Demo được gì |
|---|---|---|
| 1 | Nền móng: Maven multi-module, docker-compose, Flyway, CI, ArchUnit | App chạy, CI xanh |
| 2 | `identity`: login, refresh rotation, RBAC, test chống IDOR | Gọi API có token |
| 3 | `catalog`: phim/rạp/phòng/ghế/suất chiếu + `EXCLUDE` constraint + seed 10 phim, 3 rạp, 200 suất | Duyệt lịch chiếu thật |
| **4-5** | **`booking`: hold/release/seat map + concurrency test 200 thread + xử lý deadlock** | **Đỉnh của dự án** |
| 6 | `payment`: mock gateway → VNPay sandbox; idempotency; outbox + relay; job đối soát | Đặt vé trọn vòng |
| 7 | `cinebook-worker`: sweeper, notification, WebSocket realtime | Ghế hết hạn tự nhả, UI thấy ngay |
| 8 | UI tối giản: seat map realtime + trang thanh toán | Quay được video demo |
| 9 | Observability + k6 + tối ưu + README có biểu đồ | Có số liệu trước/sau |
| 10 | Đệm: deploy, video demo, tài liệu kiến trúc | Sẵn sàng đưa vào CV |

Hai lưu ý:

- **Làm mock payment gateway trước, VNPay sandbox sau.** Không để tiến độ phụ thuộc sandbox của bên thứ ba ngay từ đầu.
- **Tuần 10 là đệm thật sự**, không phải tuần làm việc.

---

## 11. Seam chừa sẵn cho AI Agent (Phase 2)

Phase 2 xây agent hội thoại tìm kiếm và đặt vé bằng function calling. Năm điểm nối dưới đây được làm ngay ở phase 1 để phase 2 không phải mổ lại core:

**1. Logic nằm ở use-case service, không nằm trong controller.** Controller chỉ chuyển đổi HTTP ↔ DTO. Agent sẽ gọi thẳng `HoldSeatsUseCase` như một hàm Java, không đi vòng qua HTTP. Đây là seam quan trọng nhất và miễn phí — chỉ là kỷ luật viết code.

**2. `findAdjacentAvailableSeats(showtimeId, count, preference)`.** Câu *"còn 2 ghế trống cạnh nhau ở hàng giữa"* dịch thẳng ra hàm này. Bản thân nó là bài toán SQL thú vị (quét theo hàng ghế tìm dải liên tiếp, ưu tiên hàng giữa) và UI phase 1 dùng luôn cho nút "gợi ý ghế đẹp".

**3. `ShowtimeSearchQuery` với filter phong phú** — thể loại, quận, khoảng thời gian, số ghế còn trống. Agent cần lọc mềm; UI cũng cần.

**4. `Idempotency-Key` đã có sẵn.** LLM retry tool call là chuyện thường.

**5. `audit_log.actor_type`** (`USER` / `AGENT` / `SYSTEM`) có mặt từ phase 1.

Nguyên tắc: **agent không phải hệ thống song song**. Nó là mặt tiền hội thoại gọi vào đúng những use case mà UI đang gọi. Tool của agent chỉ là lớp vỏ mỏng bọc quanh service có sẵn.

---

## 12. Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Tuần 4-5 (booking core) trượt tiến độ | Đây là phần quan trọng nhất — nếu trượt thì cắt UI (tuần 8) xuống còn trang seat map, bỏ trang thanh toán |
| VNPay sandbox khó tích hợp hoặc không cấp được tài khoản | Mock gateway làm trước và luôn giữ lại; VNPay là tuỳ chọn |
| Concurrency test flaky trong CI | Chạy trên Testcontainers với seed cố định; nếu vẫn flaky thì tách sang job CI riêng, không chặn build chính |
| Ôm đồm quá nhiều công nghệ | Danh sách "ngoài phạm vi" ở mục 1.3 là ràng buộc, không phải gợi ý |
| Chưa từng dùng Kafka / Testcontainers | Milestone tuần 1 đã dựng sẵn hạ tầng và một test mẫu chạy được trước khi đụng vào nghiệp vụ |
