# cinebook

![CI](https://github.com/trantho1615/cinebook/actions/workflows/ci.yml/badge.svg)

He thong dat ve xem phim. Backend Java 25 + Spring Boot 4, tap trung vao ba bai toan:
tranh chap ghe dong thoi, thanh toan bat dong bo, va observability co so lieu.

Thiet ke chi tiet: [docs/specs/2026-08-11-cinebook-core-design.md](docs/specs/2026-08-11-cinebook-core-design.md)

## Yeu cau

| | Ban |
|---|---|
| JDK | 25 (LTS) |
| Maven | 3.9+ |
| Docker + Compose | bat buoc, ke ca khi chi chay test |

`JAVA_HOME` phai tro toi JDK 25. Kiem tra:

```bash
"$JAVA_HOME/bin/java" -version    # phai ra 25.x
```

## Chay local

```bash
docker compose up -d

mvn -B -pl cinebook-api    spring-boot:run   # cong 8080
mvn -B -pl cinebook-worker spring-boot:run   # cong 8081
```

Health check nam tren CONG QUAN TRI, khong phai cong nghiep vu:

- api: http://localhost:8090/actuator/health
- worker: http://localhost:8091/actuator/health

Cong 8080/8081 chi phuc vu nghiep vu. Actuator tach sang 8090/8091 vi
/actuator/prometheus ke ten endpoint, so nguoi dung va nhip giao dich — no thuoc ve mang
noi bo chu khong phai Internet.

Giam sat: Prometheus http://localhost:9090, Grafana http://localhost:3000 (dashboard
"cinebook" nap san tu ops/grafana/dashboards), Jaeger http://localhost:16686.

Build truoc khi chay worker phai la `mvn clean install`, KHONG phai `mvn package`: fat jar
cua worker goi cinebook-api lay tu ~/.m2, nen `package` co the dong goi mot ban api cu ma
khong bao gi. Dung moi tien trinh java dang chay truoc khi `clean` — Windows khoa file jar.

Do tai: xem `load-test/README.md` va so lieu o `docs/ket-qua-do-tai.md`.

Ket qua dang chu y nhat: seat map p95 **1,05 s -> 34 ms** (30 lan) sau khi bo mot vong lap
doc bang gia 96 lan moi request. Duong giu ghe cung nhanh len 4,6 lan du khong sua dong nao
cua no — nut that la connection pool dung chung.

![Dashboard trong luc do tai](docs/images/grafana-flash-sale.jpg)

### Chay voi du lieu mau

```bash
mvn -B -pl cinebook-api spring-boot:run -Dspring-boot.run.profiles=demo
```

Nap 10 phim, 3 rap, 9 phong (96 ghe moi phong) va 252 suat chieu trong 7 ngay toi.
Bo nap la idempotent: chay lai khong tao du lieu trung.

Thu nhanh:

```bash
curl "http://localhost:8080/movies?status=NOW_SHOWING"
curl "http://localhost:8080/showtimes?city=Ho%20Chi%20Minh&district=Quan%201"
```

## Chay test

```bash
mvn -B verify
```

Integration test chay tren PostgreSQL 18 that qua Testcontainers, **khong dung H2** —
partial index, `EXCLUDE USING gist` va `FOR UPDATE SKIP LOCKED` la nen tang cua thiet ke
nay va H2 khong ho tro chung. Lan chay dau se mat them thoi gian de keo image
`postgres:18-alpine`.

Bo test khong phu thuoc `docker compose`: no tu dung container rieng, nen chay duoc
tren may sach va tren CI.

## Cau truc

| Module | Vai tro |
|---|---|
| `common` | Contract dung chung giua cac deployable |
| `cinebook-api` | Phuc vu REST va WebSocket, cong 8080 |
| `cinebook-worker` | Scheduled job va Kafka consumer, cong 8081, khong co endpoint nghiep vu |

Hai luat kien truc duoc ep o tang build, khong dua vao ky luat ca nhan:

- Module chi duoc phu thuoc vao package `api` cua module khac (`ModuleBoundaryTest`)
- `cinebook-worker` khong duoc khai bao `@RestController` hay `@Controller` (`WorkerApplicationTest`)

## Ha tang local

| Service | Ban | Cong |
|---|---|---|
| PostgreSQL | 18-alpine | 5432 |
| Redis | 8-alpine | 6379 |
| Kafka | 4.3.1 (KRaft) | 9092 |

## Tai lieu

- [Thiet ke core booking (phase 1)](docs/specs/2026-08-11-cinebook-core-design.md)
- [Ke hoach milestone 1 — nen mong](docs/plans/2026-08-11-m1-foundation.md)
