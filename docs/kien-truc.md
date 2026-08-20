# Kiến trúc cinebook

Tài liệu này viết cho người muốn hiểu **vì sao** hệ thống được dựng như vậy, không phải danh sách công nghệ đã dùng. Mỗi vấn đề đi kèm liên kết tới test chứng minh — vì một lời khẳng định không có test thì chỉ là một ý định.

## Hai deployable, năm module

```
                     ┌──────────────────────────────┐
   trình duyệt ──────│   cinebook-api (monolith)    │
   REST + WebSocket  │  identity │ catalog │ booking │
   cổng thanh toán ──│  payment  │ notification      │
        webhook      └───┬──────────┬────────┬───────┘
                         │          │        │
                  PostgreSQL 18  Redis 8   Kafka
                     (nguồn      (pub/sub  (outbox relay)
                      sự thật)   realtime)      │
                                                ▼
                                    ┌──────────────────┐
                                    │ cinebook-worker  │
                                    │ • sweeper        │
                                    │ • outbox relay   │
                                    │ • đối soát       │
                                    │ • gửi thông báo  │
                                    └──────────────────┘
```

**`cinebook-api`** phục vụ mọi request đồng bộ, không chạy scheduled job nào.
**`cinebook-worker`** không nhận HTTP request nghiệp vụ nào; nó phụ thuộc `cinebook-api` như một thư viện và kích hoạt đúng những use-case cần chạy nền qua một danh sách `@Import` tường minh.

Ranh giới này có ý nghĩa vận hành cụ thể: **worker chết thì hệ thống vẫn bán được vé**, chỉ mất tính kịp thời của việc dọn dẹp và gửi thông báo. Điều đó không phải lời hứa suông — [`SweeperTest.khong_co_sweeper_thi_van_giu_duoc_ghe_da_het_han_cua_nguoi_khac`](../cinebook-api/src/test/java/com/cinebook/booking/SweeperTest.java) chứng minh nó.

Hai luật kiến trúc được ép ở tầng build, không dựa vào kỷ luật cá nhân:

- Module chỉ được phụ thuộc vào package `api` của module khác — [`ModuleBoundaryTest`](../cinebook-api/src/test/java/com/cinebook/architecture/ModuleBoundaryTest.java). Luật này đã bắt lỗi trong thiết kế của chính tôi **bốn lần** qua chín milestone.
- Worker không được khai báo `@RestController` — [`WorkerApplicationTest`](../cinebook-worker/src/test/java/com/cinebook/worker/WorkerApplicationTest.java).

---

## Vấn đề 1: Hai người cùng bấm một ghế

**Cách giải.** Một `UNIQUE INDEX` bộ phận trên `seat_hold (showtime_id, seat_id) WHERE status IN ('HELD','BOOKED')`. Database là nơi phân xử, không phải code ứng dụng — giữa lúc `SELECT` kiểm tra và lúc `INSERT` luôn có một khe thời gian, và dưới tải thì khe đó đủ rộng để hai người cùng lọt.

Câu lệnh giữ ghế dùng `INSERT ... ON CONFLICT DO NOTHING RETURNING seat_id`, rồi so số dòng trả về với số ghế yêu cầu. Một câu lệnh vừa quyết định vừa ghi, không có khe hở, và biết **chính xác** ghế nào bị mất để báo lại cho người dùng.

**Đánh đổi.** Index bộ phận chỉ chứa dòng `HELD`/`BOOKED`, nên bảng tích luỹ hàng trăm nghìn dòng `EXPIRED` vẫn không làm chậm đường nóng. Đổi lại: một hold **đã quá hạn** vẫn chiếm chỗ trong index cho tới khi được `UPDATE` sang `EXPIRED` — đó là lý do có bước dọn lười ở đầu mỗi lượt giữ ghế.

**Chứng minh.** [`ConcurrentSeatHoldTest`](../cinebook-api/src/test/java/com/cinebook/booking/ConcurrentSeatHoldTest.java) — 200 luồng ảo cùng giành một ghế, khẳng định **đúng 1 thắng, 199 nhận 409, 0 lỗi khác**, và một truy vấn bất biến trả về 0 dòng vi phạm.

## Vấn đề 2: Giữ nhiều ghế cùng lúc thì deadlock

**Cách giải.** Sắp xếp `seat_id` trước khi ghi, ở hai tầng độc lập: Java `.sorted()` và `ORDER BY` trong SQL. Hai transaction giữ hai ghế theo thứ tự ngược nhau là công thức kinh điển của deadlock.

**Điều bất ngờ khi đo.** Tôi từng viết trong comment rằng bỏ `ORDER BY` sẽ tái hiện được deadlock. **Sai.** Thí nghiệm ba cấu hình cho kết quả: chỉ sắp xếp ở Java → không deadlock; chỉ `ORDER BY` ở SQL → không deadlock; **bỏ cả hai → deadlock**. Mỗi lớp một mình đã đủ; giữ cả hai là phòng thủ theo chiều sâu. Comment trong code đã được sửa lại theo số liệu thật.

**Chứng minh.** [`DeadlockRegressionTest`](../cinebook-api/src/test/java/com/cinebook/booking/DeadlockRegressionTest.java).

## Vấn đề 3: Ghế giữ rồi bỏ đó

**Cách giải — hai lớp, và phân vai rõ ràng:**

| Lớp | Cơ chế | Đảm bảo |
|---|---|---|
| Lười | `UPDATE` hold hết hạn ngay trong transaction giữ ghế | **Đúng đắn** — không bao giờ có ghế bị khoá oan |
| Sweeper | Job 30 giây/lần trong worker + ShedLock | **Kịp thời** — UI người khác thấy ghế trống ra |

Tính đúng đắn **không** phụ thuộc worker. Tắt sweeper thì người thứ hai vẫn giữ được ghế đã hết hạn của người thứ nhất.

**Đánh đổi có chủ ý.** Đường lười giải phóng ghế của người khác nhưng **không đụng vào dòng `bookings` của họ** — sửa booking của người khác trong transaction của mình mở thêm một hướng deadlock mới. Booking đó nằm `PENDING` cho tới khi sweeper dọn, và trong lúc đó nó không thể confirm được vì bước confirm yêu cầu `seat_hold.status = 'HELD'`.

**Chứng minh.** [`SweeperTest`](../cinebook-api/src/test/java/com/cinebook/booking/SweeperTest.java), [`HoldExpiryTest`](../cinebook-api/src/test/java/com/cinebook/booking/HoldExpiryTest.java), và [`SchedulerLockTest`](../cinebook-worker/src/test/java/com/cinebook/worker/SchedulerLockTest.java) cho phần khoá phân tán.

## Vấn đề 4: Thanh toán bất đồng bộ, và mọi cách nó hỏng

Cổng thanh toán ẩn sau interface `PaymentGateway` với một cài đặt giả **luôn được giữ lại** — để tiến độ không phụ thuộc việc xin được tài khoản sandbox.

| Tình huống | Cách xử lý |
|---|---|
| Webhook đến hai lần | Dedupe bằng `UNIQUE (provider, provider_event_id)`, **luôn trả 200** — trả 4xx thì cổng retry mãi |
| Webhook sai chữ ký | Ghi lại payload thô với `signature_valid = false`, trả 401, không xử lý. Đây là thứ cứu bạn khi phải điều tra một giao dịch tranh chấp ba tuần sau |
| Webhook **đến muộn**, ghế đã có chủ mới | `UPDATE seat_hold ... WHERE status='HELD'` ảnh hưởng 0 dòng → **tự động hoàn tiền**, vẫn trả 200 |
| Webhook **không bao giờ đến** | Job đối soát quét giao dịch treo quá 15 phút rồi chủ động hỏi cổng, và **đi lại đúng đường mà webhook đi** — không có hai nhánh logic cho cùng một kết quả |

**Một cái bẫy đáng nhớ.** `confirm()` ném exception để báo webhook đến muộn. Nếu nó dùng chung transaction với phía gọi thì transaction bị đánh dấu `rollback-only`, và lệnh hoàn tiền ngay sau sẽ chết lúc commit với `UnexpectedRollbackException`. Cách sửa: `@Transactional(propagation = REQUIRES_NEW)`. Tạm gỡ nó ra thì 3/4 test đỏ với đúng thông báo đó.

**Chứng minh.** [`WebhookIdempotencyTest`](../cinebook-api/src/test/java/com/cinebook/payment/WebhookIdempotencyTest.java), [`LateWebhookTest`](../cinebook-api/src/test/java/com/cinebook/payment/LateWebhookTest.java), [`ReconciliationTest`](../cinebook-api/src/test/java/com/cinebook/payment/ReconciliationTest.java).

## Vấn đề 5: Event không được mất

**Cách giải — transactional outbox.** Nếu `COMMIT` database rồi mới gọi `kafkaTemplate.send()`, tiến trình chết giữa hai lệnh là **mất event vĩnh viễn** — vé đã đặt nhưng email không bao giờ gửi. Outbox biến việc ghi event thành một phần của chính transaction nghiệp vụ.

Relay đọc bằng `FOR UPDATE SKIP LOCKED`, nên **nhiều instance chạy song song là tính năng**, không phải vấn đề: mỗi instance nhận một tập dòng rời nhau, không cần leader election. Đã kiểm chứng bằng hai phiên psql: relay 2 nhận ngay dòng 4–6 trong khi relay 1 còn đang giữ dòng 1–3.

Vì vậy `OutboxRelayJob` **cố ý không mang** `@SchedulerLock` — bọc khoá lên nó là tự tay biến một thiết kế scale ngang thành một thiết kế một-instance. [`ScheduledJobPolicyTest`](../cinebook-worker/src/test/java/com/cinebook/worker/ScheduledJobPolicyTest.java) ép luật này ở tầng build.

**Đánh đổi.** Giao hàng at-least-once, nên **mọi consumer phải idempotent**. Bảng `notifications` có `UNIQUE (event_id)` và use-case dùng `ON CONFLICT DO NOTHING RETURNING` — cùng đúng mẫu đã dùng cho ghế.

**Chứng minh.** [`OutboxTest`](../cinebook-api/src/test/java/com/cinebook/payment/OutboxTest.java) (có test khẳng định event bị rollback cùng thay đổi nghiệp vụ), [`KafkaPublishTest`](../cinebook-worker/src/test/java/com/cinebook/worker/KafkaPublishTest.java), [`NotificationConsumerTest`](../cinebook-worker/src/test/java/com/cinebook/worker/NotificationConsumerTest.java).

## Vấn đề 6: Chậm ở đâu

**Cách giải: đo trước, tối ưu sau.** Kịch bản k6 flash sale (50 VU, 60 giây) cho `seat map p95 = 1,05 s`. Nhưng `EXPLAIN (ANALYZE, BUFFERS)` cho thấy hai truy vấn của endpoint đó cộng lại **chưa tới 0,4 ms**, và CPU chỉ 12 %.

Thứ thực sự cạn là **connection pool**: `hikaricp_connections_active = 10/10`, `pending` có lúc **30**. Đọc kỹ thì `findDetail` gọi `priceQuery.priceFor(...)` **trong vòng lặp qua 96 ghế**, mỗi lần lại `findAll()` bảng giá — một lượt xem sơ đồ ghế là **99 truy vấn**, không phải 3.

Sửa: đọc bảng giá **một lần** rồi dùng cho cả phòng.

| | Trước | Sau |
|---|---|---|
| seat map p95 | 1,05 s | **34 ms** (30×) |
| giữ ghế p95 | 111 ms | 24 ms (4,6×) |
| pool chờ (đỉnh) | 30 | 0 |

**Đường giữ ghế nhanh lên 4,6 lần dù không sửa một dòng nào của nó** — bằng chứng rằng nút thắt là tài nguyên dùng chung.

**Không thêm cache Redis**, dù đó là giả thuyết đứng đầu ban đầu: sau khi sửa, p95 cách ngưỡng 300 ms rất xa. Thêm một lớp có thể trả dữ liệu cũ để đổi lấy khoản lợi mà số liệu không đòi là đi ngược nguyên tắc của chính milestone đó.

**Chứng minh.** [`docs/ket-qua-do-tai.md`](ket-qua-do-tai.md) có số liệu kèm điều kiện đo, [`PriceTableTest`](../cinebook-api/src/test/java/com/cinebook/catalog/PriceTableTest.java) giữ tính đúng đắn của phần tối ưu.

---

## Những gì cố ý chưa làm

Người phỏng vấn giỏi sẽ hỏi về những chỗ trống. Đây là danh sách, kèm lý do:

| Chưa làm | Lý do |
|---|---|
| Tích hợp cổng thanh toán thật (VNPay) | `PaymentGateway` đã sẵn sàng, chỉ cần thêm một cài đặt. Không để tiến độ phụ thuộc việc xin tài khoản sandbox |
| Rate limit, virtual waiting room | Cân nhắc từ đầu và **quyết định không làm** — nó là một hướng riêng, không phải phần còn thiếu của hướng đã chọn |
| Trace nối từ request HTTP sang worker | Outbox **cố ý cắt** chuỗi đồng bộ. Muốn nối phải lưu `traceparent` vào `outbox_events` rồi khôi phục ở relay. Là lựa chọn, không phải thiếu sót |
| Span cho từng truy vấn DB | Cần thêm `datasource-micrometer`. `EXPLAIN ANALYZE` và metric HikariCP đã đủ để tìm ra điểm nghẽn |
| Kubernetes, blue-green | Hai deployable thì compose là đủ |
| Refresh token trong cookie `HttpOnly` | Đúng cho sản phẩm thật, kéo theo CSRF token và cấu hình `SameSite`. UI hiện dùng `sessionStorage` và ghi rõ đánh đổi trong code |

## Đọc thêm

- [Thiết kế chi tiết (spec gốc)](specs/2026-08-11-cinebook-core-design.md)
- [Số liệu đo tải](ket-qua-do-tai.md)
- [Kế hoạch từng milestone](plans/) — chín milestone, mỗi cái ghi lại cả những chỗ tôi làm sai và cách phát hiện
