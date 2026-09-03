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

**Luật INBOUND — chỉ ba cái:**

| Cổng | Nguồn | Vì sao |
|---|---|---|
| 22 | **Chỉ IP của bạn** (`My IP`) | SSH. Mở cho `0.0.0.0/0` là mời cả thế giới thử mật khẩu |
| 80 | `0.0.0.0/0` | Let's Encrypt cần cổng này để xác minh tên miền |
| 443 | `0.0.0.0/0` | HTTPS |

**Không mở inbound gì khác.** Đặc biệt không mở 5432, 6379, 9092, 3000 — Postgres đang dùng mật khẩu `cinebook/cinebook` và Redis không có mật khẩu.

Về `My IP`: AWS điền vào đó **IP public** của máy bạn — địa chỉ mà console nhìn thấy request đến từ đó, không phải `192.168.x.x` trong máy. IP nhà thường là động, nên hôm sau SSH treo ở `Connection timed out` thì việc đầu tiên là vào đây bấm lại `My IP`. Đang bật VPN thì luật sẽ ghim IP của VPN, tắt VPN là mất quyền vào.

**Luật OUTBOUND — giữ nguyên mặc định `All traffic → 0.0.0.0/0`, đừng xoá.**

Ba luật ở trên chỉ nói về chiều vào. Security group là **stateful**: traffic trả lời cho một kết nối inbound đã được cho phép thì tự động ra được, nên xoá luật outbound **không** làm SSH hỏng — bạn vẫn vào máy bình thường và tưởng mọi thứ ổn. Cái chết là những kết nối **do máy tự khởi tạo**: `apt`, tải Docker, kéo image, và Caddy gọi Let's Encrypt. Chúng bị drop im lặng, biểu hiện là treo đúng 300 giây rồi `curl: (28) Timeout was reached`.

Muốn siết chặt thì tối thiểu phải mở outbound TCP 443, TCP 80, UDP 53, UDP 123. Bề mặt tấn công nằm ở chiều vào, không phải chiều ra.

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

### Nếu dùng DuckDNS (hoặc dynamic DNS miễn phí khác)

DuckDNS **thu hồi subdomain nếu 30 ngày liền không có lần cập nhật nào**. Với Elastic IP tĩnh thì sẽ không bao giờ có gì tự động gọi lên DuckDNS — nên tên miền chết lặng lẽ sau một tháng, thường là đúng lúc có người mở link.

Đặt cron giữ tên miền sống, tiện thể tự sửa IP nếu sau này đổi máy:

```bash
# Token lay o https://www.duckdns.org/ sau khi dang nhap
read -rsp 'DuckDNS token: ' DUCK_TOKEN; echo
echo "$DUCK_TOKEN" | sudo tee /etc/duckdns.token >/dev/null
sudo chmod 600 /etc/duckdns.token

sudo tee /usr/local/bin/duckdns-update.sh >/dev/null <<'EOF'
#!/bin/sh
# Bo trong tham so ip= de DuckDNS tu lay IP nguon cua request.
curl -fsS "https://www.duckdns.org/update?domains=TEN-SUBDOMAIN&token=$(cat /etc/duckdns.token)&ip=" \
  >> /var/log/duckdns.log 2>&1
EOF
sudo chmod 700 /usr/local/bin/duckdns-update.sh

# Chay thu ngay: log phai ghi "OK", ghi "KO" la token sai
sudo /usr/local/bin/duckdns-update.sh && sudo tail -1 /var/log/duckdns.log

( sudo crontab -l 2>/dev/null; echo '0 */6 * * * /usr/local/bin/duckdns-update.sh' ) | sudo crontab -
sudo crontab -l
```

## 3. Chuẩn bị máy

```bash
ssh -i key.pem ubuntu@<Elastic IP>

# Swap 2 GB. Maven build trong container can nhieu RAM hon ban tuong; may 4 GB dang chay
# container khac se cham tran va build chet giua chung.
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# swapon khong in gi khi thanh cong, nen phai tu kiem tra.
swapon --show && free -h
```

Trước khi cài Docker, **chờ cho lần khởi động đầu tiên yên tĩnh đã**. Ubuntu 24.04 chạy `cloud-init` và `unattended-upgrades` trong vài phút đầu; chúng giữ lock của apt, và script Docker chạy `apt-get -qq ... >/dev/null` nên thông báo "Waiting for cache lock" bị nuốt mất — bạn chỉ thấy màn hình đứng im không lý do.

```bash
sudo cloud-init status --wait
while sudo fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1; do echo "cho apt..."; sleep 5; done
```

Rồi mới cài Docker:

```bash
# Tai roi chay, KHONG phai curl | sh: co file thi doc duoc truoc khi chay, va -x in tung
# buoc ra man hinh thay vi im lang vai phut.
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh -x get-docker.sh

sudo usermod -aG docker ubuntu
newgrp docker

# Phai chay duoc KHONG can sudo truoc khi di tiep.
docker run --rm hello-world
```

`newgrp docker` mở một shell con — prompt trông y hệt nhưng đó là shell mới.

> **Nếu `curl` treo rồi báo `(28) Timeout was reached`:** máy không ra được Internet. Gần như luôn là luật **outbound** của security group đã bị xoá — xem lại mục 1. Kiểm tra nhanh trên máy:
> ```bash
> curl -sS -m 10 -o /dev/null -w '%{http_code}\n' https://checkip.amazonaws.com
> ```

## 4. Lấy mã nguồn và đặt bí mật

```bash
git clone https://github.com/trantho1615/cinebook && cd cinebook
```

Sinh bí mật thật. **Không `cp .env.example .env`** — file đó chỉ để đọc cho biết có những biến nào, mọi giá trị trong đó là chuỗi giữ chỗ. Tạo thẳng `.env` bằng giá trị ngẫu nhiên:

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

**Bước 1, 2, 3 và 5 chạy trên server. Bước 4 BẮT BUỘC chạy từ máy khác** — lý do ở ngay dưới.

```bash
# 1. HTTPS song, va chung chi la that chu khong phai tu ky.
curl -s -o /dev/null -w 'GET /         -> %{http_code}  (mong doi 200)\n' https://ten-mien-cua-ban.com
curl -s -o /dev/null -w 'chung chi TLS -> %{ssl_verify_result}  (mong doi 0)\n' https://ten-mien-cua-ban.com

# 2. API tra loi. -s de curl im lang: head -c dong ong som lam curl bao
#    "(23) Failure writing output to destination", vo hai nhung gay hoang mang.
curl -s https://ten-mien-cua-ban.com/showtimes | head -c 200; echo

# 3. WebSocket qua duoc reverse proxy. Day la thu de vo nhat khi co Caddy dung giua, va
#    buoc 2 khong chung minh duoc gi cho no: /showtimes la HTTP thuong.
#    -D - in header ngay khi nhan; -m 5 vi khi bat tay THANH CONG thi ket noi duoc nang cap
#    va mo mai, curl se treo chu khong tu ket thuc — thanh cong moi la cai lam no dung hinh.
curl -s -m 5 -D - -o /dev/null --http1.1 \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  https://ten-mien-cua-ban.com/ws | head -5      # mong doi: 101 Switching Protocols

# 5. Container va RAM
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}"
swapon --show; free -h
```

Không container nào được ở trạng thái `Restarting`.

**Bước 4 — cổng hạ tầng phải đóng.** Chạy **từ laptop của bạn**, không phải từ server: AWS không cho một instance tự gọi vào Elastic IP của chính nó, nên chạy trên server thì **mọi cổng đều báo đóng, kể cả 443 đang mở** — phép đo vô nghĩa mà nhìn rất đẹp.

Vì thế danh sách dưới đây có **443 làm đối chứng dương**. Nếu 443 không hiện `MO` thì phép đo hỏng, làm lại chứ đừng đọc kết quả.

```powershell
# Windows PowerShell
443,5432,6379,9092,3000,9090,16686,8080 | ForEach-Object {
  $r = Test-NetConnection ten-mien-cua-ban.com -Port $_ -WarningAction SilentlyContinue
  '{0,-6} {1}' -f $_, $(if ($r.TcpTestSucceeded) { 'MO' } else { 'dong' })
}
```

```bash
# macOS / Linux
for p in 443 5432 6379 9092 3000 9090 16686 8080; do
  nc -z -w3 ten-mien-cua-ban.com $p && echo "$p MO" || echo "$p dong"
done
```

Kết quả đúng: **443 `MO`**, bảy cổng còn lại `dong`.

Rồi mở trình duyệt: đăng nhập bằng nút **"Dùng tài khoản demo"**, đặt một vé, và **mở hai cửa sổ để kiểm tra ghế đổi màu realtime** — đây là thứ dễ vỡ nhất khi có reverse proxy đứng giữa.

## 7. Xem dashboard giám sát

Grafana, Prometheus và Jaeger **cố ý không mở ra Internet**. Xem qua SSH tunnel:

```bash
ssh -i key.pem -L 3000:localhost:3000 -L 9090:localhost:9090 -L 16686:localhost:16686 ubuntu@<Elastic IP>
```

Rồi mở `http://localhost:3000` trên máy mình, đăng nhập `admin` với mật khẩu trong `.env`.

---

## Bản public này là một bản demo, không phải hệ thống thật

Bản đang chạy: **https://cinebookapp.duckdns.org**

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
| `curl: (28) Timeout was reached`, `apt` treo, không kéo được image | Luật **outbound** của security group bị xoá. SSH vẫn vào được nên rất dễ tưởng mạng ổn — xem mục 1 |
| `usermod: group 'docker' does not exist` | Bước cài Docker phía trên đã thất bại chứ không phải chỉ chậm. Đọc lại lỗi của nó |
| Caddy không xin được chứng chỉ | DNS chưa lan, cổng 80 chưa mở inbound, hoặc cổng 443 chưa mở **outbound** (Caddy phải gọi ra Let's Encrypt) |
| `docker compose` báo `required variable ... is missing` | Chưa tạo `.env`, hoặc thiếu một biến. Đó là bảo vệ có chủ ý |
| Build chết giữa chừng, không rõ lý do | Hết RAM. Kiểm tra swap đã bật chưa: `free -h` |
| Ghế không đổi màu realtime | WebSocket không qua được proxy. Kiểm tra log Caddy và chắc chắn đang truy cập qua `https://ten-mien` chứ không phải IP |
| Ổ đĩa đầy sau vài lần deploy | `docker image prune -f`, mỗi lần build để lại image cũ |
| Máy chậm bất thường | `docker stats` — nhiều khả năng một JVM ăn quá phần. Kiểm tra `JAVA_TOOL_OPTIONS` có được áp không |
