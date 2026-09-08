# cinebook

[![CI](https://github.com/trantho1615/cinebook/actions/workflows/ci.yml/badge.svg)](https://github.com/trantho1615/cinebook/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25%20LTS-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue)

A cinema ticket booking system built around three problems that CRUD does not solve: **seat
contention** when many people click at once, **asynchronous payments** whose webhooks arrive
late or never, and **finding bottlenecks by measurement** rather than by intuition.

**Live demo — [cinebookapp.duckdns.org](https://cinebookapp.duckdns.org)**

Sign in with the *Dùng tài khoản demo* (demo account) button; no registration required. To
see the part that matters, open **two browser windows** on the same showtime, hold a seat in
one, and watch the other update instantly. The payment gateway is simulated — no real money
is involved. The user interface is in Vietnamese.

![Grafana dashboard captured during a load test](docs/images/grafana-flash-sale.jpg)

## Results

| Metric | Result |
|---|---|
| 200 virtual threads racing for one seat | exactly **1** succeeds, 199 receive `409`, **0** other errors |
| Seat map p95 under load | **1.05 s → 34 ms** after profiling and optimisation (30×) |
| Test suite | **174 tests** against real PostgreSQL — no H2, no mocked database |

Every important invariant is covered by a test **that has been observed failing**. Before a
safety net is trusted here, it is deliberately removed to confirm it actually tears.

Absolute numbers depend on the machine. The measurement conditions are documented in full
alongside the results.

## Architecture

```
                     ┌──────────────────────────────┐
        browser ─────│   cinebook-api (monolith)    │
   REST + WebSocket  │  identity │ catalog │ booking │
 payment gateway ────│  payment  │ notification      │
        webhook      └───┬──────────┬────────┬───────┘
                         │          │        │
                  PostgreSQL 18  Redis 8   Kafka
                   (source of    (realtime  (outbox relay)
                     truth)       pub/sub)      │
                                                ▼
                                    ┌──────────────────┐
                                    │ cinebook-worker  │
                                    │ sweeper, relay,  │
                                    │ reconciliation,  │
                                    │ notifications    │
                                    └──────────────────┘
```

Two deployable applications. **If the worker dies, tickets can still be sold** — only the
timeliness of cleanup and notifications is lost. That is not a claim in prose; a test holds
it in place.

Two architectural rules are enforced at build time rather than by discipline:

- A module may depend only on another module's `api` package — `ModuleBoundaryTest`
- The worker may not declare a `@RestController` — `WorkerApplicationTest`

**[→ Architecture guide](docs/kien-truc.md)** — six problems, each with its trade-offs, a
link to the test that proves the solution, and a list of what was deliberately left out.

## Tech stack

| Layer | Choice |
|---|---|
| Language / framework | Java 25 (LTS), Spring Boot 4.1, Spring Framework 7 |
| Build | Maven 3.9, multi-module |
| Database | PostgreSQL 18, Flyway migrations |
| Cache / pub-sub | Redis 8 |
| Messaging | Apache Kafka 4.3.1 (KRaft) |
| Testing | JUnit 5, Testcontainers, AssertJ, ArchUnit, Awaitility |
| Load testing | k6 |
| Observability | Micrometer, Prometheus, Grafana, OpenTelemetry, Jaeger |
| Deployment | Docker Compose, Caddy with automatic HTTPS, AWS EC2 (arm64) |

## Quick start

Docker is the only requirement — no local Java or Maven installation is needed.

```bash
git clone https://github.com/trantho1615/cinebook && cd cinebook
cp .env.example .env
docker compose --profile full up --build
```

Then open **http://localhost:8080** and sign in with the demo account button.

| Service | Address |
|---|---|
| Application | http://localhost:8080 |
| Grafana | http://localhost:3000 (the `cinebook` dashboard is provisioned from this repo) |
| Prometheus | http://localhost:9090 |
| Jaeger | http://localhost:16686 |

## Development

<details>
<summary>Running from source, tests, and load tests</summary>

### Requirements

| | Version |
|---|---|
| JDK | 25 (LTS), with `JAVA_HOME` set correctly |
| Maven | 3.9+ |
| Docker + Compose | required, including for running tests |

### Running while editing

Start infrastructure only and run the applications from an IDE or Maven, instead of
rebuilding images on every change:

```bash
docker compose up -d

mvn -B -pl cinebook-api    spring-boot:run -Dspring-boot.run.profiles=demo   # 8080
mvn -B -pl cinebook-worker spring-boot:run                                   # 8081
```

**Build the worker with `mvn clean install`, not `mvn package`.** The worker's fat jar
resolves `cinebook-api` from the local Maven repository, so `package` can silently bundle a
stale copy — the worker then runs old code and only its behaviour drifts. Stop running Java
processes before `clean`; Windows locks jar files.

### Separate management port

Health and metrics are served on **8090** (api) and **8091** (worker), not on 8080/8081.
`/actuator/prometheus` exposes endpoint names, user counts and transaction rates, so it
belongs on an internal network. A test asserts that the business port leaks no metrics.

### Tests

```bash
mvn -B verify
```

Integration tests run against real PostgreSQL 18 through Testcontainers, **not H2**: partial
indexes, `EXCLUDE USING gist` and `FOR UPDATE SKIP LOCKED` are the foundation of this design
and H2 does not support them.

Run `clean verify` **with `docker compose` stopped**. A test once passed only because a
Compose Redis container happened to be running locally, and CI was what caught it.

### Load testing

The k6 flash-sale scenario lives in [`load-test/README.md`](load-test/README.md). Full
results with measurement conditions are in [`docs/ket-qua-do-tai.md`](docs/ket-qua-do-tai.md).

</details>

## Deployment

The public demo runs on a single AWS EC2 `t4g.medium` (arm64) instance behind Caddy, which
obtains and renews Let's Encrypt certificates automatically. Infrastructure ports — Postgres,
Redis, Kafka, Grafana, Prometheus, Jaeger — are closed to the internet and reachable only
over an SSH tunnel.

The deployed instance runs the `prod,demo` profile: strict about secrets, but with sample
showtimes and the simulated payment gateway enabled so that visitors have something to click.

## Documentation

- [Architecture guide](docs/kien-truc.md) — six problems and how each was solved
- [Load test results](docs/ket-qua-do-tai.md) — before/after numbers with measurement conditions
- [Load test scenario](load-test/README.md) — how to reproduce the numbers
- [Secrets policy](SECURITY.md) — what the secret-shaped strings in this repository are

> The deep-dive documents are written in Vietnamese.

## Deliberately not implemented

Listed so that their absence reads as a decision rather than an oversight: rate limiting and
a virtual waiting room, distributed tracing across the outbox boundary, per-query database
spans, Kubernetes and blue-green deployment, and refresh tokens in `HttpOnly` cookies. The
reasoning for each is in the [architecture guide](docs/kien-truc.md).
