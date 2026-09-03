# Bí mật trong repo này

Tài liệu này tồn tại để trả lời một câu cụ thể: **những chuỗi trông giống mật khẩu trong repo này là gì, và cái nào đáng lo.**

## Chưa từng có bí mật thật nào được commit

Kiểm tra được bằng lệnh:

```bash
git log --all --oneline --name-only -- .env .env.local "*.pem" "*.key"
```

Kết quả rỗng. `.env` nằm trong `.gitignore` từ đầu; mọi giá trị thật chỉ tồn tại trên máy chạy.

## Không còn giá trị mặc định chạy được trong mã nguồn

`application.yml` từng có:

```yaml
secret: ${CINEBOOK_JWT_SECRET:mot-gia-tri-mac-dinh-chay-duoc}
```

Đó là một lỗ hổng đang chờ, không phải vì chuỗi đó bí mật, mà vì nó **hoạt động**: một lần deploy quên đặt biến môi trường thì hệ thống vẫn lên bình thường, và bất kỳ ai đọc repo cũng ký được token cho bất kỳ tài khoản nào.

Bây giờ:

| Ngữ cảnh | Hành vi |
|---|---|
| Không đặt biến (máy lập trình viên) | [`SecretResolver`](cinebook-api/src/main/java/com/cinebook/shared/config/SecretResolver.java) sinh bí mật **ngẫu nhiên cho lần chạy này** và in cảnh báo to. Token không sống qua lần khởi động sau |
| Profile `prod` | `application-prod.yml` khai `${CINEBOOK_JWT_SECRET}` **không có giá trị mặc định** → thiếu biến là ứng dụng chết ngay lúc khởi động |
| `docker-compose.prod.yml` | `${CINEBOOK_JWT_SECRET:?}` → compose từ chối khởi động |

Ba lớp này được [`ProdConfigTest`](cinebook-api/src/test/java/com/cinebook/config/ProdConfigTest.java) canh.

## Những chuỗi còn lại và vì sao chúng không phải bí mật

| Chuỗi | Ở đâu | Vì sao chấp nhận được |
|---|---|---|
| `POSTGRES_PASSWORD: cinebook` | `docker-compose.yml`, `application.yml` | Database chạy trong container trên máy lập trình viên. Bản `docker-compose.prod.yml` **đóng hẳn cổng 5432**, không expose ra ngoài. Không cấp quyền vào bất cứ thứ gì thật |
| `MatKhauRatManh123` | Các file test | Mật khẩu của tài khoản giả do test tự tạo rồi tự xoá |
| Giá trị trong `.env.example` | `.env.example` | Chuỗi giữ chỗ, ghi rõ phải thay. Kèm lệnh `openssl rand -base64 48` để sinh giá trị thật |

Nếu công cụ quét báo động về những dòng này, đó là **báo động đúng mẫu hình nhưng sai ngữ cảnh** — đánh dấu là chấp nhận được, đừng sửa bằng cách giấu chúng đi.

## Nếu bạn deploy repo này

1. `cp .env.example .env`
2. Sinh giá trị thật:
   ```bash
   openssl rand -base64 48   # CINEBOOK_JWT_SECRET
   openssl rand -base64 32   # CINEBOOK_WEBHOOK_SECRET
   ```
3. `chmod 600 .env`
4. Đổi mật khẩu Postgres nếu database đó chứa dữ liệu thật.

`docker-compose.prod.yml` khai các biến này dưới dạng `${VAR:?}`, nên thiếu bất kỳ biến nào là compose từ chối khởi động kèm tên biến còn thiếu — không có đường nào chạy lên với cấu hình nửa vời.
