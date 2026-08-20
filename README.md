# cinebook

![CI](https://github.com/trantho1615/cinebook/actions/workflows/ci.yml/badge.svg)

He thong dat ve xem phim, Java 25 + Spring Boot 4. Du an tap trung vao ba bai toan **khong
giai duoc bang CRUD**: tranh chap ghe khi nhieu nguoi cung bam, thanh toan bat dong bo voi
webhook den tre hoac khong den, va tim diem nghen bang so lieu thay vi bang linh cam.

![Dashboard trong luc do tai](docs/images/grafana-flash-sale.jpg)

## Ba con so

| | |
|---|---|
| **200 luong** cung gianh mot ghe | dung **1** thanh cong, 199 nhan 409, **0** loi khac |
| seat map duoi tai (p95) | **1,05 s → 34 ms** sau khi do va toi uu (30 lan) |
| **171 test** tren PostgreSQL that | khong dung H2, khong mock database |

Moi bat bien quan trong deu co test **da tung thay do**: truoc khi tin mot luoi an toan,
toi go no ra de xem thu co that su rach khong.

## Chay thu

Chi can Docker. Khong can cai Java hay Maven.

```bash
git clone https://github.com/trantho1615/cinebook && cd cinebook
cp .env.example .env
docker compose --profile full up --build
```

Roi mo **http://localhost:8080**, bam "Dung tai khoan demo".

Muon thay phan hay nhat: mo **hai cua so** cung mot suat chieu, giu ghe o cua so nay va
nhin cua so kia doi mau ngay lap tuc.

| | Dia chi |
|---|---|
| UI | http://localhost:8080 |
| Grafana | http://localhost:3000 (dashboard "cinebook" nap san tu repo) |
| Prometheus | http://localhost:9090 |
| Jaeger | http://localhost:16686 |

## Kien truc

```
                     ┌──────────────────────────────┐
   trinh duyet ──────│   cinebook-api (monolith)    │
   REST + WebSocket  │  identity │ catalog │ booking │
   cong thanh toan ──│  payment  │ notification      │
        webhook      └───┬──────────┬────────┬───────┘
                         │          │        │
                  PostgreSQL 18  Redis 8   Kafka
                    (nguon      (pub/sub   (outbox relay)
                     su that)    realtime)      │
                                                ▼
                                    ┌──────────────────┐
                                    │ cinebook-worker  │
                                    │ sweeper, relay,  │
                                    │ doi soat, email  │
                                    └──────────────────┘
```

Hai deployable. **Worker chet thi he thong van ban duoc ve** — chi mat tinh kip thoi cua
viec don dep va gui thong bao. Do khong phai loi hua suong, co test giu no.

Hai luat kien truc duoc ep o tang build:

- Module chi duoc phu thuoc vao package `api` cua module khac (`ModuleBoundaryTest`)
- Worker khong duoc khai bao `@RestController` (`WorkerApplicationTest`)

**[→ Doc tai lieu kien truc](docs/kien-truc.md)** — sau van de, moi van de kem link toi test
chung minh, va danh sach nhung gi co y chua lam.

## Danh cho nguoi muon sua code

<details>
<summary>Chay bang Maven, chay test, do tai</summary>

### Yeu cau

| | Ban |
|---|---|
| JDK | 25 (LTS), `JAVA_HOME` phai tro dung |
| Maven | 3.9+ |
| Docker + Compose | bat buoc, ke ca khi chi chay test |

### Chay khi dang sua code

Chi bat ha tang, chay ung dung tu IDE hoac Maven — khong phai build image moi lan:

```bash
docker compose up -d

mvn -B -pl cinebook-api    spring-boot:run -Dspring-boot.run.profiles=demo   # 8080
mvn -B -pl cinebook-worker spring-boot:run                                   # 8081
```

**Build truoc khi chay worker phai la `mvn clean install`, KHONG phai `mvn package`.** Fat
jar cua worker goi `cinebook-api` lay tu `~/.m2`, nen `package` co the dong goi mot ban api
cu ma khong bao gi — worker se chay code cu va chi lech hanh vi. Dung moi tien trinh java
truoc khi `clean`: Windows khoa file jar.

### Cong quan tri tach rieng

Health va metric nam o **8090** (api) va **8091** (worker), khong phai 8080/8081.
`/actuator/prometheus` ke ten endpoint, so nguoi dung va nhip giao dich — no thuoc ve mang
noi bo. Co test khang dinh cong nghiep vu khong lo metric.

### Chay test

```bash
mvn -B verify
```

Integration test chay tren PostgreSQL 18 that qua Testcontainers, **khong dung H2** —
partial index, `EXCLUDE USING gist` va `FOR UPDATE SKIP LOCKED` la nen tang cua thiet ke nay
va H2 khong ho tro chung.

Chay `clean verify` **khi da tat `docker compose`**: mot lan Milestone 6 co test xanh gia vi
Redis cua compose dang chay, va CI moi bat duoc.

### Do tai

Kich ban k6 flash sale: [`load-test/README.md`](load-test/README.md).
So lieu day du kem dieu kien do: [`docs/ket-qua-do-tai.md`](docs/ket-qua-do-tai.md).

</details>

## Tai lieu

- [Kien truc — sau van de va cach giai](docs/kien-truc.md)
- [Thiet ke chi tiet (spec goc)](docs/specs/2026-08-11-cinebook-core-design.md)
- [Ket qua do tai truoc/sau](docs/ket-qua-do-tai.md)
- [Ke hoach tung milestone](docs/plans/) — chin milestone, moi cai ghi lai ca nhung cho lam
  sai va cach phat hien ra
