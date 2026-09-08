# Kết quả đo tải

Tài liệu này ghi lại **hai chiến dịch đo khác nhau**, trả lời hai câu hỏi khác nhau:

| | Câu hỏi | Kịch bản | Kết quả chính |
|---|---|---|---|
| Phần 1 | Khi tranh chấp thì ai thắng, và mất bao lâu? | `constant-vus`, có `sleep` | seat map p95 **1,05 s → 34 ms** |
| [Phần 2](#thông-lượng-và-khối-lượng-dữ-liệu) | Đẩy đến bao nhiêu req/s thì gãy, và gãy ở đâu? | `constant-arrival-rate`, không `sleep`, 12 triệu dòng | p95 ở 2 000 RPS **234 ms → 25,6 ms** |

Con số tuyệt đối phụ thuộc máy. Thứ đáng đọc là **tỉ lệ trước/sau trên cùng một máy**.

## Điều kiện đo

Một con số không có điều kiện đo là một con số vô nghĩa. Mọi số liệu dưới đây đo trong đúng bối cảnh này:

| Hạng mục | Giá trị |
|---|---|
| Máy | Windows 11, 24 CPU logic. **Mọi thứ chạy trên cùng một máy**: Postgres, Redis, Kafka, Prometheus, Grafana trong Docker; `cinebook-api` và `cinebook-worker` chạy bằng `java -jar` trên host |
| Sinh tải | k6 1.5.0 trong container, gọi qua `host.docker.internal:8080` |
| Thời lượng | 60 giây, 50 VU (40 người xem, 8 người giành ghế, 2 người mua) |
| Dữ liệu | Profile `demo`: 10 phim, 3 rạp, 9 phòng, 96 ghế mỗi phòng, ~200 suất chiếu trong tương lai |
| Vùng tranh chấp | Hai hàng D và E của **một** suất chiếu — 24 ghế cho 10 VU giành nhau |
| Kết nối DB | HikariCP mặc định, tối đa 10 |

Con số tuyệt đối phụ thuộc máy. Thứ đáng đọc là **tỉ lệ trước/sau trên cùng một máy**, không phải giá trị tuyệt đối.

## Baseline — trước khi tối ưu

Lần chạy ngày 2026-08-20, commit trước khi có tối ưu nào.

### Phía client (k6 đo, tức thứ người dùng thật cảm nhận)

| Đường | avg | p90 | p95 | max |
|---|---|---|---|---|
| `GET /showtimes/{id}/seats` | 250 ms | 494 ms | **1,05 s** ✗ | 1,17 s |
| `POST /showtimes/{id}/holds` | 74 ms | 93 ms | **111 ms** ✓ | 283 ms |

| Chỉ số | Giá trị |
|---|---|
| Tổng request | 1 689 (26,3 req/s) |
| Tỉ lệ lỗi | **0,00 %** |
| Giữ ghế thành công | 22 |
| Giữ ghế xung đột (409) | 518 |
| Tỉ lệ xung đột | **95,9 %** |

Ngưỡng `p95 < 300 ms` cho seat map **không đạt**. Ngưỡng của đường giữ ghế đạt thoải mái.

### Phía server (Micrometer đo)

| Đường | p95 | p99 |
|---|---|---|
| `/showtimes/{showtimeId}/seats` | 971 ms | 1 055 ms |
| `/showtimes/{showtimeId}/holds` | 108 ms | — |

Server-side p95 (971 ms) gần như trùng client-side p95 (1,05 s). **Độ trễ nằm trong ứng dụng**, không phải ở mạng hay ở k6.

### Tài nguyên

| Chỉ số | Giá trị |
|---|---|
| `hikaricp_connections_active` | **10 / 10** — pool cạn |
| `hikaricp_connections_pending` (đỉnh) | 3, có lần chạy lên tới **30** |
| CPU tiến trình api | 12 % |
| CPU toàn máy | 41 % |

## Điểm nghẽn: không phải SQL, mà là số lượt đi lấy kết nối

`EXPLAIN (ANALYZE, BUFFERS)` trên chính hai truy vấn của seat map, với dữ liệu thật:

```
Index Scan using idx_seat_hold_showtime on seat_hold   Execution Time: 0.026 ms
Sort + Bitmap Heap Scan on seats (96 dòng)             Execution Time: 0.33 ms
```

Hai truy vấn cộng lại chưa tới **0,4 ms**, trong khi endpoint mất **971 ms** ở p95. Vậy thời gian không nằm ở database.

Đọc `SeatMapQueryJdbc` cùng `ShowtimeQueryJdbc.findDetail`, thoạt nhìn một lượt gọi seat map thực hiện **ba lượt truy vấn tách rời**: header của suất chiếu, 96 ghế của phòng, và các hold đang hiệu lực. Ba lượt đó **không nằm trong một transaction**, nên mỗi lượt mượn và trả một connection riêng.

Nhưng đọc kỹ hơn thì con số không phải ba (xem phần tiếp theo). Điều chắc chắn ngay ở bước này: với 40 người xem đồng thời, pool 10 connection trở thành hàng đợi — `active = 10/10`, `pending` có lúc lên 30 — trong khi CPU chỉ 12 %.

**Kết luận: hệ thống không thiếu CPU và không chậm ở SQL. Nó xếp hàng chờ kết nối.**

## Tối ưu: đọc bảng giá một lần thay vì 96 lần

Đọc kỹ `ShowtimeQueryJdbc.findDetail` cùng `PriceQueryJpa.priceFor` cho thấy con số thật:

```java
for (Map<String, Object> row : jdbc.queryForList(SQL_SEATS, ...)) {   // 96 ghế
    seats.add(new SeatView(..., priceQuery.priceFor(basePrice, seatType)));
}                                    // ↑ mỗi lần gọi lại findAll() bảng price_rules
```

Một lượt xem sơ đồ ghế không phải 3 lượt truy vấn mà là **99**: 1 header + 1 seats + **96 lượt đọc bảng giá** + 1 lượt đọc hold. Mỗi lượt mượn và trả một connection từ pool 10.

Điều đáng nói: chính lớp `PriceQueryJpa` đã ghi chú ngay từ đầu rằng nó cố ý đọc lại mỗi lần, kèm câu *"tối ưu khi đã đo, không tối ưu vì linh cảm"*. Đây là lúc đã đo.

**Cách sửa: `PriceQuery.bangGia()` trả một bản chụp, đọc một lần rồi dùng cho cả 96 ghế.** Bản chụp chỉ sống trong phạm vi một lần gọi — **không phải cache**, nên không có chuyện trả giá cũ. Không thêm Redis, không thêm TTL, không thêm gì phải vô hiệu hoá.

Một thay đổi duy nhất, rồi đo lại.

## Sau khi tối ưu

Cùng máy, cùng 50 VU, cùng 60 giây, cùng dữ liệu, cùng kịch bản.

| Đường | | Trước | Sau | Nhanh hơn |
|---|---|---|---|---|
| seat map | avg | 250 ms | **12,5 ms** | 20× |
| seat map | p90 | 494 ms | 22,2 ms | 22× |
| seat map | **p95** | **1,05 s** ✗ | **34,4 ms** ✓ | **30×** |
| seat map | max | 1,17 s | 72,2 ms | 16× |
| giữ ghế | avg | 74 ms | **16,6 ms** | 4,5× |
| giữ ghế | p95 | 111 ms | **24,3 ms** | 4,6× |
| toàn hệ | throughput | 26,3 req/s ¹ | **29,0 req/s** ¹ | +10 % |
| toàn hệ | tỉ lệ lỗi | 0,00 % | 0,00 % | — |

Cả hai ngưỡng đều đạt. Phía server (Micrometer đo): seat map p95 **971 ms → 17,6 ms**.

> ¹ **26,3 req/s không phải giới hạn của hệ thống.** Kịch bản này dùng `constant-vus` với `sleep(1)`/`sleep(2)` mỗi vòng lặp, nên 50 VU chỉ có thể sinh ra chừng đó tải — con số do **chính phép đo** áp đặt. Đo đúng cách, cùng hệ thống này chịu **2 000 RPS với p95 25,6 ms**: xem [Thông lượng và khối lượng dữ liệu](#thông-lượng-và-khối-lượng-dữ-liệu) ở cuối trang.

### Vì sao nhanh hơn — cơ chế, không phải phép màu

| Chỉ số | Trước | Sau |
|---|---|---|
| `hikaricp_connections_active` (đỉnh) | **10 / 10** (cạn) | **0** |
| `hikaricp_connections_pending` (đỉnh) | 3, có lần 30 | **0** |
| CPU tiến trình api | 12 % | 3,5 % |

Bỏ 96 lượt truy vấn mỗi request khiến connection không còn là hàng đợi. Pool 10 vốn không thiếu — nó bị **một vòng lặp** làm cạn.

**Đường giữ ghế cũng nhanh lên 4,6 lần dù không sửa một dòng nào của nó.** Đó là bằng chứng rõ nhất rằng nút thắt là tài nguyên dùng chung: khi seat map thôi giữ hết connection, mọi đường khác thở được.

### Những gì KHÔNG cải thiện

- **Tỉ lệ xung đột vẫn ~96 %** (560 xung đột / 20 thành công). Đúng như mong đợi: đó là do kịch bản dựng 10 VU giành 24 ghế, không liên quan gì tới tốc độ.
- **Throughput chỉ tăng 10 %** dù độ trễ giảm 30 lần. Cũng đúng: k6 chạy `constant-vus` với `sleep` cố định giữa các vòng, nên số request bị nhịp ngủ khống chế chứ không bị độ trễ khống chế. Muốn đo throughput tối đa thì phải đổi sang executor `constant-arrival-rate` — một bài đo khác.
- **Cache Redis cho seat map: không làm.** Giả thuyết đứng đầu ở mục 2.3 của spec hoá ra không cần thiết: sau khi bỏ vòng lặp 96 lượt, p95 còn 34 ms, cách ngưỡng 300 ms rất xa. Thêm một lớp cache lúc này là thêm một thứ có thể trả dữ liệu cũ để đổi lấy một khoản lợi mà số liệu không đòi.

## Những gì cần nhớ khi đọc lại

- **Tỉ lệ xung đột 95,9 % là do kịch bản cố ý dựng**: 10 VU giành 24 ghế. Nó không nói lên rằng hệ thống thật sẽ có 96 % người dùng thất bại — nó nói rằng đường tranh chấp vẫn giữ đúng bất biến dưới áp lực cực đại, và không có lỗi 5xx nào.
- **409 không được tính là lỗi.** Lần chạy đầu tiên tôi đặt `responseCallback` sai chỗ và k6 báo `http_req_failed = 26,97 %` — đúng bằng số lượt 409. Con số đó vô nghĩa. Cách đúng là `http.setResponseCallback()` ở init context.
- **Độ trễ seat map dao động đáng kể giữa các lần chạy** (p95 594 ms → 908 ms → 1,05 s trên cùng cấu hình). Máy này chạy cả hạ tầng lẫn ứng dụng lẫn bộ sinh tải, nên nhiễu là điều phải chấp nhận. Vì vậy phần "sau khi tối ưu" phải đo lại **cùng máy, cùng lúc, cùng kịch bản**, và nên chạy vài lần rồi lấy khoảng.


## Trace phân tán

Sau khi bật OpenTelemetry, Jaeger nhận trace từ cả hai deployable (`cinebook-api`, `cinebook-worker`), và mọi dòng log mang `traceId`/`spanId` — dán id từ log vào Jaeger là thấy đường đi.

**Nối được:** `outboxRelayJob.relay` → `booking.events send` → `booking.events process` nằm trong **cùng một trace**:

```
task outboxRelayJob.relay   150,87 ms
  booking.events send        77,40 ms   (producer)
  booking.events process     34,67 ms   (consumer)
```

Chỉ có được sau khi bật `spring.kafka.template.observation-enabled` và `spring.kafka.listener.observation-enabled`. Không bật thì producer và consumer là hai trace rời rạc.

**KHÔNG nối được — và đây là hệ quả trực tiếp của thiết kế outbox:** request HTTP ban đầu (`POST /demo/payments/{id}/succeed`) không nối với trace của relay. Lý do: outbox **cố ý cắt** chuỗi đồng bộ — api chỉ ghi một dòng vào bảng và commit; một tiến trình khác đọc dòng đó vài trăm mili giây sau, trong một trace của riêng nó. Bảng `outbox_events` không mang trace context.

Muốn nối thì phải thêm cột `trace_context` vào `outbox_events`, ghi `traceparent` lúc `OutboxWriter.write(...)`, rồi khôi phục context ở relay trước khi publish. Chưa làm — nhưng ghi ra đây để người đọc biết đó là lựa chọn chứ không phải thiếu sót bị bỏ quên.

**Không có span cho từng truy vấn DB.** Micrometer không tự đo `JdbcTemplate`; muốn có thì phải thêm `datasource-micrometer-spring-boot`. Với dự án này thì `EXPLAIN ANALYZE` và metric HikariCP đã đủ để tìm ra điểm nghẽn, nên chưa thêm.

## Một cái bẫy của build

Fat jar của `cinebook-worker` **gói `cinebook-api` lấy từ `~/.m2`**, không phải từ thư mục `target/` vừa build. Quan sát được: sau `mvn -DskipTests package` toàn reactor, jar worker vẫn chứa `cinebook-api-0.1.0-SNAPSHOT.jar` cũ hai ngày (thiếu hẳn `SweepExpiredHoldsUseCase`), và worker chết lúc khởi động với `FileNotFoundException: class path resource [...SweepExpiredHoldsUseCase.class] cannot be opened`.

`mvn clean install` (sau khi **dừng mọi tiến trình đang chạy** — Windows khoá file jar và `clean` sẽ thất bại) cho ra jar đúng.

Đây là loại lỗi nguy hiểm vì im lặng: worker có thể chạy code api cũ mà không báo gì, chỉ lệch hành vi. Lệnh build trước khi chạy worker phải là `clean install`, không phải `package`.


## Metric nghiệp vụ trong lúc k6 chạy

Đo qua **chính datasource của Grafana** (không phải hỏi thẳng Prometheus), tức đúng đường mà panel dùng:

| Truy vấn của panel | Giá trị lúc đang chạy tải |
|---|---|
| `sum by (ket_qua) (rate(cinebook_seat_hold_seconds_count[1m]))` | ~9,8 lượt/giây, toàn bộ mang nhãn `xung_dot` |
| `histogram_quantile(0.95, ... cinebook_seat_hold_seconds_bucket ...)` | 17 ms |
| `cinebook_outbox_pending` | **0** — relay theo kịp, không có event nào đọng |
| `histogram_quantile(0.95, ... http_server_requests_seconds_bucket{uri=~".*seats.*"} ...)` | 22,3 ms |
| `cinebook_sweeper_released_total` | chưa có số trong 5 phút — đúng, vì kịch bản tự huỷ booking chứ không để hold hết hạn |

### Dashboard trong lúc chạy tải

![Dashboard cinebook trong lúc k6 chạy](images/grafana-flash-sale.jpg)

Sáu panel, đọc từ trái sang:

| Panel | Đọc được gì |
|---|---|
| Độ trễ HTTP theo endpoint (p95) | Đường `/showtimes/{id}/seats` nằm dưới 50 ms trong suốt đợt tải — sau tối ưu |
| Lượt giữ ghế theo kết quả | Đỉnh ~9,5 lượt/giây, tách riêng `thanh_cong` và `xung_dot` |
| Tỉ lệ giữ ghế bị xung đột | Tụt về 0 % khi hết đợt tranh chấp |
| Event đang chờ trong outbox | Phẳng ở 0 — relay theo kịp, không có event nào đọng |
| Ghế sweeper đã nhả (cộng dồn) | 6 ghế, đúng bằng dòng log `Sweeper nha 6 ghe het han` của worker |
| Độ trễ giữ ghế (p95/p99) | 10–40 ms |

**Ảnh này lúc đầu không chụp được**: phiên trình duyệt tự động render ra khung trống dù dashboard nạp đúng và truy vấn có số liệu. Nguyên nhân là extension **Dark Reader** — đúng thứ đã làm sai lệch việc kiểm tra giao diện trước đó. Tắt nó đi là panel hiện bình thường. Ai chụp lại để đưa vào tài liệu thì nhớ tắt trước.

**Một panel phải sửa vì nó vô dụng trong demo**: "Ghế sweeper nhả" ban đầu dùng `increase(cinebook_sweeper_released_total[5m])`, và counter vừa xuất hiện thì `increase` không vẽ gì — panel nằm `No data` suốt dù worker vừa nhả 6 ghế. Đổi sang `sum(cinebook_sweeper_released_total)` (cộng dồn) thì thấy ngay.
---

# Thông lượng và khối lượng dữ liệu

Phần trên trả lời câu *"khi tranh chấp thì ai thắng, và mất bao lâu"*. Nó chưa bao giờ trả lời hai câu khác: **đẩy đến bao nhiêu request/giây thì hệ thống gãy**, và **khi bảng có hàng triệu dòng thì truy vấn nào xuống cấp**.

## Điều kiện đo

| Hạng mục | Giá trị |
|---|---|
| Máy | Windows 11, 24 CPU logic |
| Chạy | Postgres + Redis trong Docker; `cinebook-api` bằng `java -jar` trên host; k6 trong container |
| **Không** chạy | `cinebook-worker` (outbox relay sẽ cố đẩy event lên Kafka và làm nhiễu), Kafka, Prometheus, Grafana, Jaeger |
| Sinh tải | k6 1.5.0, executor `constant-arrival-rate`, **không `sleep`** |
| Bậc tải | 100 → 250 → 500 → 1 000 → 2 000 → 4 000 req/s, mỗi bậc 30 giây |
| Đường đo | `GET /showtimes/{id}/seats`, suất chiếu lấy ngẫu nhiên từ 5 000 suất đã sinh |
| HikariCP | mặc định, tối đa 10 |

## Dữ liệu

Profile `demo` có ~477 suất chiếu và 864 ghế. Ở kích thước đó **không index nào kịp có ý nghĩa** — Postgres quét tuần tự vài trăm dòng còn nhanh hơn đi qua index, nên `EXPLAIN ANALYZE` không nói được gì.

`load-test/seed-large.sql` nạp dữ liệu ở quy mô một chuỗi rạp sau nhiều năm:

| Bảng | Số dòng | Tổng | Dữ liệu | Index |
|---|---:|---:|---:|---:|
| `seat_hold` | 12 000 094 | 2 301 MB | 1 677 MB | 624 MB |
| `bookings` | 2 000 105 | 444 MB | 269 MB | 174 MB |
| `outbox_events` | 2 000 033 | 376 MB | 332 MB | 43 MB |
| `showtimes` | 200 475 | 77 MB | 24 MB | 53 MB |

Ràng buộc khó nhất khi sinh không phải khối lượng mà là **ghế phải thuộc đúng phòng của suất chiếu**: `seat_hold.seat_id` chỉ có khoá ngoại tới `seats`, không ai ép nó cùng phòng. Sinh sai thì truy vấn seat map không join được dòng nào, và ta có một bảng 12 triệu dòng mà đường nóng không chạm tới dòng nào — mọi con số sau đó đều đẹp và vô nghĩa. Đã kiểm: **0 dòng sai trên 12 triệu**.

## Điểm gãy trước khi sửa

| mục tiêu | đạt được | % | med | p95 | p99 | lỗi % |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 100 | 100 % | 5,6 | 7,7 | 9,9 | 0,00 |
| 250 | 250 | 100 % | 4,8 | 6,8 | 9,3 | 0,00 |
| 500 | 491 | 98 % | 4,5 | 7,5 | 11,3 | 0,01 |
| 1 000 | 981 | 98 % | 4,5 | 11,2 | 273,4 | 0,60 |
| 2 000 | 1 951 | 98 % | 28,4 | 234,1 | 349,0 | 0,00 |
| 4 000 | **1 907** | 48 % | 1 546,9 | 2 184,6 | 4 109,2 | 0,14 |

**Trần ≈ 1 900 RPS.** Bằng chứng nằm ở hai bậc cuối: tăng gấp đôi tải chào từ 2 000 lên 4 000 **không sinh thêm một request nào** (1 951 → 1 907, thực tế còn giảm). Thứ duy nhất tăng là độ trễ. Đó là chữ ký của bão hoà, không phải của thiếu tải.

## Điểm nghẽn: số vòng mạng, không phải SQL

`GET /showtimes/{id}/seats` chạy **bốn** truy vấn, mỗi truy vấn mượn và trả kết nối riêng vì đường đọc không có `@Transactional`.

| Truy vấn | SQL thuần (đo trong plpgsql) | Qua JDBC từ host | Phần không phải SQL |
|---|---:|---:|---:|
| `SELECT 1` (đối chứng) | ~0 | **0,658** | 0,658 |
| `price_rules` findAll | 0,0045 | 0,592 | 0,588 |
| header (showtime+phim+rạp) | 0,0514 | 0,655 | 0,603 |
| 96 ghế của phòng | 0,0330 | 0,870 | 0,837 |
| hold còn hiệu lực | 0,0151 | 0,645 | 0,630 |
| **Tổng** | **0,104 ms** | **2,762 ms** | **2,658 ms (96 %)** |

Dòng `SELECT 1` là bằng chứng quyết định: một truy vấn **không làm gì cả** vẫn tốn 0,658 ms. Đó là giá cố định của việc hỏi Postgres bất cứ điều gì, và nó áp đúng bốn lần cho mỗi request.

Cùng loại lỗi với phần trên (96 lượt truy vấn), chỉ ở quy mô nhỏ hơn. **Điểm nghẽn lại không nằm ở chỗ ai cũng nhìn vào.**

### Thiết kế index đứng vững ở 12 triệu dòng

```
Index Scan using idx_seat_hold_showtime on seat_hold  (actual time=0.490..0.873 rows=10 loops=1)
  Index Cond: (showtime_id = '6bfb...'::uuid)
  Filter: ((status = 'BOOKED') OR ((status = 'HELD') AND (expires_at > now())))
  Buffers: shared hit=11
Execution Time: 0.892 ms
```

Planner chứng minh được vị từ của truy vấn kéo theo vị từ của partial index. Bảng 12 triệu dòng nhưng chỉ chạm **11 buffer**. `INSERT ... ON CONFLICT` — lá chắn chống double-booking — mất **0,094 ms**.

## Đã sửa

| | Việc | Bỏ được |
|---|---|---|
| A | Cache `price_rules` với TTL 10 giây | 1 vòng mạng |
| B | Cache sơ đồ ghế theo phòng (chỉ phần tĩnh, giá vẫn tính theo suất) | 1 vòng mạng |
| C | `limit`/`offset` cho `GET /showtimes` | — |

**Không truy vấn nào được tối ưu.** Cả bốn đã nhanh sẵn.

## Sau khi sửa

| mục tiêu | trước: đạt / p95 | sau: đạt / p95 | p95 |
|---:|---:|---:|---:|
| 250 | 250 / 6,8 ms | 248 / 5,3 ms | 1,3× |
| 500 | 491 / 7,5 ms | 495 / 6,2 ms | 1,2× |
| 1 000 | 981 / 11,2 ms | **1 000** / 6,0 ms | 1,9× |
| 2 000 | 1 951 / 234,1 ms | 1 987 / **25,6 ms** | **9,1×** |
| 4 000 | 1 907 / 2 184,6 ms | **2 303** / 1 902,4 ms | 1,1× |

Tài nguyên ở bậc 2 000:

| | `hikari.active` | `pending` | Postgres CPU |
|---|---:|---:|---:|
| Trước | **10 / 10** | **186–190** | 145–167 % |
| Sau | **5** | **0** | 78–91 % |

## Nâng pool: đã đo, và quyết định không làm

Giả thuyết sau khi thấy `pending = 190`: nâng `maximum-pool-size` sẽ nâng trần. Đo bốn giá trị ở cùng bậc 4 000 RPS:

| pool | đạt được | med | p95 | p99 |
|---:|---:|---:|---:|---:|
| **10** (mặc định) | **2 634** | 637,5 | **1 520,8** | **1 599,4** |
| 20 | 2 377 | 720,5 | 2 037,3 | 3 863,0 |
| 40 | 2 521 | 559,9 | 2 089,0 | 4 184,5 |
| 80 | 2 525 | 597,3 | 1 709,4 | 4 316,4 |

**Giả thuyết sai.** Chênh lệch thông lượng nằm trong mức nhiễu giữa các lần chạy, nhưng **p99 xấu đi một chiều**: 1,6 s lên 3,9–4,3 s. Nhiều kết nối hơn không tạo thêm năng lực, nó chỉ chuyển hàng đợi từ HikariCP vào trong Postgres — nơi nó thành tranh chấp và làm dài đuôi.

Bài học đọc số: **`pending = 190` là triệu chứng, không phải nguyên nhân.** Hàng đợi đầy không có nghĩa hàng đợi là chỗ nghẽn.

Giữ nguyên pool mặc định. Đây là lần thứ hai số liệu bác bỏ một thay đổi nghe có vẻ hiển nhiên — lần đầu là cache Redis.

## Điểm vận hành

| | |
|---|---|
| Thoải mái | **2 000 RPS**, p95 **25,6 ms**, pool dùng 5/10 |
| Bão hoà | ~2 300–2 600 RPS, p95 1,5–1,9 s |

## Ba chỗ dễ tự lừa mình, và cả ba đều đã suýt lừa được

**`dropped_iterations` khác 0 nghĩa là k6 không sinh đủ tải.** Ở bậc 4 000, k6 chỉ sinh được 48–58 % tải mục tiêu vì mỗi iteration kéo quá lâu nên chạm trần `maxVUs`. Con số RPS đạt được vẫn là thông lượng thật, nhưng không được đọc như *"hệ thống chịu được chừng này khi bị đè đúng 4 000"*.

**`handleSummary` của k6 nuốt lỗi.** Lần chạy đầu, hàm tổng kết ném `Cannot read property 'toFixed' of undefined` vì k6 mặc định không tính `p(50)`/`p(99)` — phải khai `summaryTrendStats`. k6 bỏ qua lỗi đó và in bảng mặc định, nên rất dễ tưởng mình đang đọc số liệu đầy đủ.

**Một test đo bằng counter chưa tồn tại thì luôn xanh.** Test kiểm tra cache ban đầu bắt `MeterNotFoundException` rồi trả 0 — hiệu của hai số 0 luôn bằng 0, nên nó **đậu khi chưa có cache**. Một phép đo không đo được thì phải gãy, không được im lặng báo xanh.
