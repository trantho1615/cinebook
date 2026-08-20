# Kết quả đo tải

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

Đọc `SeatMapQueryJdbc` cùng `ShowtimeQueryJdbc.findDetail`, một lượt gọi seat map thực hiện **ba lượt truy vấn tách rời**:

1. header của suất chiếu (join phim, phòng, rạp),
2. 96 ghế của phòng,
3. các hold đang hiệu lực.

Ba lượt đó **không nằm trong một transaction**, nên mỗi lượt mượn và trả một connection riêng. Với 40 người xem đồng thời, pool 10 connection trở thành hàng đợi: `active = 10/10` và `pending` có lúc lên 30, trong khi CPU chỉ 12 %.

**Kết luận: hệ thống không thiếu CPU và không chậm ở SQL. Nó xếp hàng chờ kết nối.**

Hai trong ba truy vấn đó lấy dữ liệu **tĩnh**: sơ đồ ghế của một phòng và thông tin phim/rạp không đổi giữa các lần gọi. Chỉ truy vấn thứ ba là động. Đây là chỗ tối ưu mà số liệu chỉ tới — trùng với giả thuyết ở mục 2.3 của spec, nhưng bây giờ có bằng chứng chứ không phải phỏng đoán.

## Sau khi tối ưu

*(Điền ở Task 4, đo lại đúng kịch bản này: cùng máy, cùng 50 VU, cùng 60 giây, cùng dữ liệu.)*

## Những gì cần nhớ khi đọc lại

- **Tỉ lệ xung đột 95,9 % là do kịch bản cố ý dựng**: 10 VU giành 24 ghế. Nó không nói lên rằng hệ thống thật sẽ có 96 % người dùng thất bại — nó nói rằng đường tranh chấp vẫn giữ đúng bất biến dưới áp lực cực đại, và không có lỗi 5xx nào.
- **409 không được tính là lỗi.** Lần chạy đầu tiên tôi đặt `responseCallback` sai chỗ và k6 báo `http_req_failed = 26,97 %` — đúng bằng số lượt 409. Con số đó vô nghĩa. Cách đúng là `http.setResponseCallback()` ở init context.
- **Độ trễ seat map dao động đáng kể giữa các lần chạy** (p95 594 ms → 908 ms → 1,05 s trên cùng cấu hình). Máy này chạy cả hạ tầng lẫn ứng dụng lẫn bộ sinh tải, nên nhiễu là điều phải chấp nhận. Vì vậy phần "sau khi tối ưu" phải đo lại **cùng máy, cùng lúc, cùng kịch bản**, và nên chạy vài lần rồi lấy khoảng.
