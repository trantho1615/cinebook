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

Health check: http://localhost:8080/actuator/health va http://localhost:8081/actuator/health

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
