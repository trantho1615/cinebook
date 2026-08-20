# Kịch bản video demo — 3 phút

Ba cảnh, mỗi cảnh chứng minh đúng **một** điều khó. Quay ngẫu hứng sẽ ra một đoạn tám phút không ai xem hết.

Người xem là nhà tuyển dụng hoặc người phỏng vấn kỹ thuật. Họ không cần thấy màn hình đăng nhập đẹp; họ cần thấy thứ mà một dự án CRUD không có.

---

## Chuẩn bị trước khi bấm ghi

- [ ] **Tắt Dark Reader cho `localhost`.** Extension này đã hai lần làm sai lệch việc kiểm tra giao diện trong dự án (Milestone 7 và 8): ghế mất hết màu, panel Grafana render ra khung trắng. Trên video thì hỏng hẳn cảnh 1.
- [ ] Chạy `docker compose --profile full up -d --build` **trước ít nhất 5 phút**. Kafka có khoảng một giây kêu `NOT_COORDINATOR` lúc khởi động nguội — vô hại nhưng nhìn xấu nếu lọt vào khung hình.
- [ ] Kiểm tra có lịch chiếu tương lai:
  ```bash
  docker exec cinebook-postgres psql -U cinebook -d cinebook \
    -tAc "SELECT count(*) FROM showtimes WHERE start_at > now()"
  ```
  Ra 0 thì khởi động lại api — seeder tự nạp bù.
- [ ] Mở sẵn **hai cửa sổ trình duyệt**: một cửa sổ thường, một cửa sổ ẩn danh. Mỗi tab có `sessionStorage` riêng nên đăng nhập được hai tài khoản khác nhau.
- [ ] Đăng nhập trước ở cả hai (nút **"Dung tai khoan demo"** ở cửa sổ A; cửa sổ B đăng ký một email khác) rồi cùng mở **một suất chiếu**.
- [ ] Mở sẵn một terminal đã `cd` vào thư mục dự án.
- [ ] Xếp cửa sổ: hai trình duyệt cạnh nhau chiếm 2/3 màn hình, terminal 1/3 bên dưới.

---

## Cảnh 1 — Ghế đổi màu realtime (60 giây)

**Chứng minh:** hai tiến trình riêng biệt gặp nhau qua Redis pub/sub, và UI thấy sự thật ngay lập tức.

| Thời điểm | Làm gì | Nói gì |
|---|---|---|
| 0:00 | Hai cửa sổ cùng một suất chiếu, cùng sơ đồ ghế | "Hai người dùng khác nhau đang mở cùng một suất chiếu." |
| 0:10 | Cửa sổ **A**: chọn D5, D6 → bấm **Giữ ghế** | "A giữ hai ghế." |
| 0:12 | **Chỉ vào cửa sổ B** — hai ghế đó vừa đổi màu | "B không bấm gì cả. Ghế đổi màu ngay, không tải lại trang." |
| 0:25 | Terminal: đẩy hold hết hạn | "Bây giờ giả lập A bỏ đi, để hold hết hạn." |
| | ```docker exec cinebook-postgres psql -U cinebook -d cinebook -c "UPDATE seat_hold SET expires_at = now() - interval '1 minute' WHERE status='HELD'; UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE status='PENDING';"``` | |
| 0:30–0:55 | Chờ tối đa 30 giây, **cả hai** cửa sổ thấy ghế trống ra | "Sweeper chạy trong tiến trình worker — một tiến trình khác hẳn — vừa nhả ghế. Cả hai cửa sổ cùng cập nhật." |

**Câu chốt:** "Tiến trình worker không phục vụ HTTP request nào. Nó nói chuyện với UI qua Redis pub/sub. Và nếu worker chết thì hệ thống vẫn bán được vé — chỉ là ghế hết hạn được dọn chậm hơn."

---

## Cảnh 2 — Đặt vé trọn vòng (60 giây)

**Chứng minh:** đường thanh toán bất đồng bộ đi trọn vẹn, và nhánh thất bại cũng đúng.

| Thời điểm | Làm gì | Nói gì |
|---|---|---|
| 0:00 | Chọn hai ghế → **Giữ ghế** | "Giữ ghế xong là có đồng hồ đếm ngược 10 phút." |
| 0:08 | **Chỉ vào đồng hồ đếm ngược**, rồi bấm **Thanh toán** | "Đây là thời gian ghế được giữ cho riêng mình." |
| 0:20 | Trang thanh toán hiện đường dẫn của cổng | "Cổng thật sẽ chuyển hướng tới đây. Trình duyệt không thể tự gọi webhook vì webhook cần chữ ký HMAC, nên demo có một cổng giả lập chỉ bật ở profile demo — ký ở phía server." |
| 0:30 | Bấm **Giả lập thành công** | "Vé xác nhận, có mã đơn." |
| 0:40 | Giữ hai ghế khác → thanh toán → **Giả lập thất bại** | "Thất bại thì ghế được nhả ra ngay, không giam của người khác." |

**Câu chốt:** "Webhook có thể đến hai lần, đến muộn, hoặc không bao giờ đến. Cả ba trường hợp đều có test: gửi trùng chỉ xử lý một lần; đến muộn khi ghế đã có chủ mới thì **tự động hoàn tiền**; không đến thì job đối soát chủ động hỏi cổng thanh toán."

---

## Cảnh 3 — Chịu tải và số liệu (60 giây)

**Chứng minh:** hệ thống giữ đúng bất biến dưới áp lực, và tôi biết nó chậm ở đâu vì đã đo.

| Thời điểm | Làm gì | Nói gì |
|---|---|---|
| 0:00 | Mở Grafana `http://localhost:3000/d/cinebook` bên cạnh terminal | |
| 0:05 | Chạy k6 | "50 người dùng ảo, trong đó 10 người giành nhau đúng 24 ghế." |
| | ```MSYS_NO_PATHCONV=1 docker run --rm -i -v "$PWD/load-test:/scripts" grafana/k6:1.5.0 run /scripts/flash-sale.js``` | |
| 0:15 | Chỉ vào panel **"Tỉ lệ giữ ghế bị xung đột"** đang leo lên ~96 % | "Gần như ai cũng đụng người khác. Đó là chủ ý — chỗ giành nhau mới đáng đo." |
| 0:30 | Chỉ vào **`http_req_failed = 0,00 %`** trong terminal | "Không một lỗi 5xx nào. 409 là kết quả nghiệp vụ đúng đắn, không phải lỗi — và bài đo được cấu hình để không đếm nhầm." |
| 0:45 | Chỉ vào panel độ trễ | "p95 của sơ đồ ghế là 34 mili giây. Trước khi tối ưu nó là 1,05 giây." |

**Câu chốt:** "Điểm nghẽn không phải SQL — hai truy vấn cộng lại chưa tới 0,4 mili giây. Nó là một vòng lặp gọi 96 truy vấn mỗi request làm cạn connection pool. Tôi tìm ra bằng cách đo, không phải bằng đoán. Sau khi sửa, đường giữ ghế cũng nhanh lên 4,6 lần dù không sửa dòng nào của nó."

---

## Ba mươi giây cuối (tuỳ chọn)

Nếu còn thời lượng, cho xem một thứ **không** ai kể trong video portfolio: một test **đang đỏ**.

Gỡ `@Transactional` khỏi `HoldSeatsUseCase`, chạy `ConcurrentSeatHoldTest`, cho thấy nó đỏ ngay, rồi lắp lại.

**Câu chốt:** "Mọi bất biến quan trọng trong dự án này đều có test mà tôi đã từng gỡ lưới an toàn ra để xem nó có thật sự rách không. Một test chưa bao giờ đỏ thì chưa chứng minh được gì."

---

## Những gì có thể hỏng trước ống kính

| Hiện tượng | Nguyên nhân đã gặp |
|---|---|
| Ghế không có màu, chỉ thấy chữ số | Dark Reader đang bật |
| Panel Grafana trắng trơn | Dark Reader, hoặc dashboard mới sửa mà Grafana chưa nạp lại (`docker compose restart grafana`) |
| Trang bảo "Phiên đăng nhập đã hết hạn" | Access token sống 15 phút. Mở sơ đồ ghế lâu thì UI tự làm mới; nếu vẫn văng thì đăng nhập lại trước khi quay |
| Danh sách suất chiếu trống | Dữ liệu demo hết hạn. Khởi động lại api, seeder tự nạp bù |
| k6 báo không tìm thấy file | Git Bash đổi `/scripts/...` thành đường dẫn Windows. Thêm `MSYS_NO_PATHCONV=1` |
| Worker không nhận event | Kafka mới khởi động. Đợi thêm một phút |

## Sau khi quay

- Cắt bỏ mọi khoảng chờ dài hơn 3 giây.
- Không thêm nhạc nền. Giọng nói giải thích là thứ có giá trị.
- Đưa link video lên đầu `README.md`.
