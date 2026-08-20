# Hướng dẫn deploy lên AWS EC2

Đưa cinebook lên một máy EC2 `t4g.medium` (ARM Graviton, 2 vCPU, 4 GB) với tên miền riêng và HTTPS.

**Chi phí:** ~$24,5 máy + ~$2,4 ổ đĩa 30 GB gp3 = **~$27/tháng**. $100 credit dùng được khoảng **3,7 tháng**.

---

## Trước khi bắt đầu: đặt Budget alert

Làm việc này **đầu tiên**, không phải cuối cùng. Credit hết thì AWS tính tiền vào thẻ chứ không tự tắt máy.

1. AWS Console → **Billing and Cost Management** → **Budgets** → **Create budget**
2. Loại **Cost budget**, ngưỡng **$80/tháng**, gửi email cảnh báo ở 80 % và 100 %.

---

## 1. Dựng máy

**EC2 → Launch instance:**

| Mục | Giá trị |
|---|---|
| AMI | **Ubuntu Server 24.04 LTS (arm64)** — phải là arm64, không phải x86_64 |
| Instance type | `t4g.medium` |
| Ổ đĩa | 30 GB gp3 |
| Key pair | Tạo mới, tải file `.pem` về và giữ kỹ |

**Security group — chỉ ba luật:**

| Cổng | Nguồn | Vì sao |
|---|---|---|
| 22 | **Chỉ IP của bạn** (`My IP`) | SSH. Mở cho `0.0.0.0/0` là mời cả thế giới thử mật khẩu |
| 80 | `0.0.0.0/0` | Let's Encrypt cần cổng này để xác minh tên miền |
| 443 | `0.0.0.0/0` | HTTPS |

**Không mở gì khác.** Đặc biệt không mở 5432, 6379, 9092, 3000 — Postgres đang dùng mật khẩu `cinebook/cinebook` và Redis không có mật khẩu.

**Gắn Elastic IP:** EC2 → Elastic IPs → Allocate → Associate vào instance. Không có nó thì IP đổi sau mỗi lần restart và bản ghi DNS thành vô nghĩa. Elastic IP gắn vào máy **đang chạy** thì miễn phí.

## 2. Trỏ tên miền

Ở nhà cung cấp tên miền, tạo bản ghi:

```
A    ten-mien-cua-ban.com    ->  <Elastic IP>
```

Kiểm tra trước khi đi tiếp — Caddy sẽ xin chứng chỉ thất bại nếu DNS chưa kịp lan:

```bash
dig +short ten-mien-cua-ban.com
```

## 3. Chuẩn bị máy

```bash
ssh -i key.pem ubuntu@<Elastic IP>

# Swap 2 GB. Maven build trong container can nhieu RAM hon ban tuong; may 4 GB dang chay
# container khac se cham tran va build chet giua chung.
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
newgrp docker
```

## 4. Lấy mã nguồn và đặt bí mật

```bash
git clone https://github.com/trantho1615/cinebook && cd cinebook
cp .env.example .env
```

Sinh bí mật thật — **đừng dùng giá trị trong `.env.example`**:

```bash
{
  echo "CINEBOOK_JWT_SECRET=$(openssl rand -base64 48)"
  echo "CINEBOOK_WEBHOOK_SECRET=$(openssl rand -base64 32)"
  echo "GRAFANA_ADMIN_PASSWORD=$(openssl rand -base64 24)"
  echo "DOMAIN=ten-mien-cua-ban.com"
} > .env
chmod 600 .env
```

Thiếu bất kỳ biến nào thì compose **từ chối khởi động** kèm thông báo rõ ràng — đó là chủ ý, không phải phiền toái.

## 5. Chạy

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile full up -d --build
```

Lần đầu mất **5–15 phút**: Maven build cả reactor bên trong image, và làm hai lần (một cho api, một cho worker). Những lần sau nhanh hơn nhiều nhờ cache.

Theo dõi:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f caddy api
```

Caddy xin chứng chỉ trong khoảng 10–30 giây đầu. Thấy dòng `certificate obtained successfully` là xong.

## 6. Kiểm chứng — đừng bỏ bước nào

```bash
# 1. HTTPS song
curl -I https://ten-mien-cua-ban.com          # 200, khong canh bao chung chi

# 2. API tra loi
curl https://ten-mien-cua-ban.com/showtimes | head -c 200

# 3. Cong ha tang PHAI dong. Chay tu MAY KHAC, khong phai tu chinh server.
for p in 5432 6379 9092 3000 9090 16686 8080; do
  nc -z -w3 ten-mien-cua-ban.com $p && echo "$p MO — SAI" || echo "$p dong — dung"
done

# 4. RAM con du
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}"
```

Rồi mở trình duyệt: đăng nhập bằng nút **"Dùng tài khoản demo"**, đặt một vé, và **mở hai cửa sổ để kiểm tra ghế đổi màu realtime** — đây là thứ dễ vỡ nhất khi có reverse proxy đứng giữa.

## 7. Xem dashboard giám sát

Grafana, Prometheus và Jaeger **cố ý không mở ra Internet**. Xem qua SSH tunnel:

```bash
ssh -i key.pem -L 3000:localhost:3000 -L 9090:localhost:9090 -L 16686:localhost:16686 ubuntu@<Elastic IP>
```

Rồi mở `http://localhost:3000` trên máy mình, đăng nhập `admin` với mật khẩu trong `.env`.

---

## Bản public này là một bản demo, không phải hệ thống thật

Nói rõ để không ai hiểu nhầm:

- Chạy với profile `prod,demo`, nên **cổng thanh toán giả lập đang bật**: ai cũng bấm được "Giả lập thành công" và xác nhận đơn mà không trả tiền. Đó là chủ ý — không có tiền thật ở đây.
- Dữ liệu demo được nạp tự động và làm mới khi lịch chiếu hết hạn.
- Không có sao lưu database. Mất máy là mất dữ liệu, và điều đó chấp nhận được với một bản demo.

Nếu sau này biến nó thành hệ thống thật: bỏ profile `demo`, tích hợp cổng thanh toán thật qua `PaymentGateway`, thêm sao lưu, và đổi mật khẩu Postgres.

## Vận hành hằng ngày

```bash
# Cap nhat ma nguon
git pull && docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile full up -d --build

# Xem log
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail 100 api worker

# Khoi dong lai mot dich vu
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart api

# Don image cu (o dia 30 GB day rat nhanh vi moi lan build tao image moi)
docker image prune -f
```

## Khi hết credit

Dừng hẳn để không bị tính tiền:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile full down
```

Rồi ở AWS Console: **Terminate** instance và **Release** Elastic IP. Elastic IP không gắn vào máy nào thì **bị tính tiền** — đây là khoản phí nhiều người quên nhất.

## Những chỗ dễ sai

| Hiện tượng | Nguyên nhân |
|---|---|
| Caddy không xin được chứng chỉ | DNS chưa lan, hoặc cổng 80 chưa mở trong security group |
| `docker compose` báo `required variable ... is missing` | Chưa tạo `.env`, hoặc thiếu một biến. Đó là bảo vệ có chủ ý |
| Build chết giữa chừng, không rõ lý do | Hết RAM. Kiểm tra swap đã bật chưa: `free -h` |
| Ghế không đổi màu realtime | WebSocket không qua được proxy. Kiểm tra log Caddy và chắc chắn đang truy cập qua `https://ten-mien` chứ không phải IP |
| Ổ đĩa đầy sau vài lần deploy | `docker image prune -f`, mỗi lần build để lại image cũ |
| Máy chậm bất thường | `docker stats` — nhiều khả năng một JVM ăn quá phần. Kiểm tra `JAVA_TOOL_OPTIONS` có được áp không |
