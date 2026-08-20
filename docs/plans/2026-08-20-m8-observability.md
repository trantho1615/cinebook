# Milestone 8 — observability và số liệu

**Goal:** Trả lời được ba câu bằng **số**, không bằng cảm giác: hệ thống chịu được bao nhiêu người cùng giành ghế, chậm ở đâu, và sau khi sửa thì nhanh hơn bao nhiêu. Kết thúc milestone là README có biểu đồ trước/sau.

**Architecture:** Micrometer trong ứng dụng → Prometheus scrape → Grafana vẽ. k6 chạy kịch bản flash sale từ ngoài. OpenTelemetry đẩy trace sang Jaeger, `traceId` xuất hiện trong log. **Metric nghiệp vụ là chính**, metric máy chỉ là nền.

**Tech Stack:** Micrometer 1.17.0, Micrometer Tracing 1.7.0, OpenTelemetry 1.62.0 (đều do Spring Boot 4.1.0 quản version), Prometheus v3.8.0, Grafana 12.3.1, Jaeger 2.11.0, k6 1.5.0.

**Spec:** `docs/specs/2026-08-11-cinebook-core-design.md` — dòng 9 của lộ trình mục 10 ("Observability + k6 + tối ưu + README có biểu đồ → có số liệu trước/sau"), và mục 2.3 ("Metric nghiệp vụ, không chỉ metric máy").

## Global Constraints

Kế thừa toàn bộ ràng buộc của Milestone 1-7. Nhắc lại những điều áp dụng trực tiếp:

- **Java 25**, **Spring Boot 4.1.0**, base package `com.cinebook`.
- **Chạy nhiều test cùng lúc dùng dấu phẩy**: `-Dtest=A,B`. Luôn đọc con số `Tests run:`, không đọc chữ `BUILD SUCCESS`.
- **`ModuleBoundaryTest` vẫn cai trị.**
- **Chạy `clean verify` khi `docker compose` đã tắt** — Milestone 6 có một test xanh giả vì Redis của compose đang chạy, CI mới bắt được.
- **Commit message không dấu**, Conventional Commits, không trailer đồng tác giả, không nhắc công cụ AI.
- **Mỗi task một commit**, `mvn -B verify` xanh trước khi commit.
- **Mọi con số trong README phải kèm điều kiện đo**: máy nào, bao nhiêu VU, bao lâu, dữ liệu gì. Một con số không có điều kiện đo là một con số vô nghĩa.

## Bằng chứng đã thu thập trước khi viết plan

| Câu hỏi | Kết quả |
|---|---|
| Boot 4.1.0 quản version nào | `micrometer 1.17.0`, `micrometer-tracing 1.7.0`, `opentelemetry 1.62.0` — đều GA. Bản mới hơn trên Maven Central là milestone (`1.18.0-M1`), **không dùng** |
| `spring-boot-actuator-autoconfigure` 4.1.0 có lớp Prometheus nào không | **Không, 0 lớp.** Autoconfiguration đã chuyển sang `spring-boot-micrometer-metrics` (23 lớp, gồm `PrometheusScrapeEndpointConfiguration`), và module đó đi kèm `spring-boot-starter-actuator` |
| Có starter riêng như `spring-boot-starter-prometheus` không | Không (404). Cách đúng: `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| Image trong compose có tồn tại không | `prom/prometheus:v3.8.0` ✓, `grafana/grafana:12.3.1` ✓, `jaegertracing/jaeger:2.11.0` ✓ (Jaeger v2, không phải `all-in-one` cũ), `grafana/k6:1.5.0` ✓ |

Việc kiểm tra thứ hai là hệ quả trực tiếp của bài học Milestone 1: **chỉ có `flyway-core` trên classpath thì Flyway không chạy, và không báo gì cả.** Boot 4 tách autoconfiguration thành module riêng, nên "có artifact trên classpath" không còn đồng nghĩa với "tính năng được bật". Task 1 phải `curl /actuator/prometheus` thật, không được tin vào việc pom đã khai đúng.

## Quyết định thiết kế cần hiểu trước khi code

**Đo trước, tối ưu sau — và tối ưu đúng thứ số liệu chỉ ra.** Plan này **không** chốt sẵn "thêm cache Redis cho seat map". Cache là giả thuyết đứng đầu (mục 2.3 của spec cũng nói vậy), nhưng Task 4 chỉ được sửa thứ mà Task 3 đo được là điểm nghẽn. Tối ưu một thứ chưa từng đo là cách chắc chắn nhất để có một đoạn code phức tạp mà không ai chứng minh được nó có ích.

**Metric nghiệp vụ mới là thứ đáng xem.** CPU và heap thì Grafana nào cũng có. Cái làm dự án này khác là những con số chỉ hệ thống này mới có: **tỉ lệ giành ghế thất bại** (bao nhiêu phần trăm lượt giữ ghế đụng phải người nhanh tay hơn), **độ trễ của outbox relay** (event nằm trong bảng bao lâu trước khi lên Kafka), **số ghế sweeper nhả mỗi vòng**. Đó là những thứ trả lời được câu "hệ thống có đang hoạt động đúng không", chứ không phải "máy có còn sống không".

**Endpoint `/actuator/prometheus` không được công khai.** Nó lộ tên endpoint, số lượng người dùng, nhịp giao dịch — đủ để dựng lại bức tranh kinh doanh. Giải pháp: **tách cổng quản trị** (`management.server.port`), api dùng 8090 và worker dùng 8091, còn 8080/8081 chỉ phục vụ nghiệp vụ. Prometheus scrape cổng quản trị; ngoài Internet không chạm tới. Có test khẳng định `/actuator/prometheus` **không** trả lời trên cổng nghiệp vụ.

**k6 chạy trong container, không cài lên máy.** Cùng lý do với Testcontainers: kịch bản load test phải chạy được trên máy người khác mà không phải cài gì thêm ngoài Docker.

**Kịch bản load test phải giống thực tế bán vé**, không phải một vòng lặp gọi một endpoint. Flash sale nghĩa là: rất nhiều người mở **cùng một suất chiếu**, đọc seat map liên tục, rồi một nhóm cùng bấm giữ **cùng một dải ghế**. Chính chỗ giành nhau đó mới là thứ Milestone 4 dựng lên để chịu, và là thứ đáng đo.

**Số liệu phải kèm điều kiện đo.** "p95 = 120 ms" là vô nghĩa nếu không nói máy nào, bao nhiêu VU, dữ liệu bao nhiêu ghế. README ghi rõ để người đọc biết con số đó nói lên điều gì — và để chính mình đo lại được sau này.

## File Structure

| File | Trách nhiệm | Task |
|---|---|---|
| `docker-compose.yml` | Thêm prometheus, grafana, jaeger | 1, 5 |
| `ops/prometheus/prometheus.yml` | Cấu hình scrape hai cổng quản trị | 1 |
| `ops/grafana/` | Datasource + dashboard dạng file, không click tay | 1 |
| `cinebook-api/pom.xml` | `micrometer-registry-prometheus` | 1 |
| `application.yml` / `cinebook-worker.yml` | Tách cổng quản trị, bật endpoint | 1 |
| `shared/metrics/BookingMetrics.java` | Counter/Timer cho luồng giữ ghế | 2 |
| `shared/metrics/OutboxMetrics.java` | Gauge độ trễ outbox | 2 |
| `MetricsExposureTest.java` | Prometheus bật, và **không** lộ ra cổng nghiệp vụ | 1 |
| `BookingMetricsTest.java` | Giữ ghế thành công/xung đột làm counter nhích đúng | 2 |
| `load-test/flash-sale.js` | Kịch bản k6 | 3 |
| `load-test/README.md` | Cách chạy, và điều kiện đo | 3 |
| `docs/ket-qua-do-tai.md` | Số liệu trước/sau kèm biểu đồ | 3, 4 |
| `shared/tracing/` + `logback-spring.xml` | traceId trong log JSON | 5 |

---

## Task 1: Prometheus scrape được, và chỉ scrape được từ bên trong

**Files:**
- Modify: `cinebook-api/pom.xml` — `micrometer-registry-prometheus`
- Modify: `cinebook-api/src/main/resources/application.yml`, `cinebook-worker/src/main/resources/cinebook-worker.yml`
- Modify: `docker-compose.yml`
- Create: `ops/prometheus/prometheus.yml`, `ops/grafana/provisioning/...`
- Test: `cinebook-api/src/test/java/com/cinebook/observability/MetricsExposureTest.java`

- [ ] **Step 1: Viết test**

```java
    /**
     * Nua thu nhat: metric phai co that.
     */
    @Test
    void endpoint_prometheus_tra_ve_metric() {
        String noiDung = client(portQuanTri).get().uri("/actuator/prometheus")
                .retrieve().body(String.class);

        assertThat(noiDung).contains("jvm_memory_used_bytes");
    }

    /**
     * Nua thu hai, va la nua de bi quen: cong nghiep vu KHONG duoc lo metric.
     * /actuator/prometheus ke ten endpoint, so nguoi dung, nhip giao dich — du de dung lai
     * buc tranh kinh doanh cho bat ky ai bam vao.
     */
    @Test
    void cong_nghiep_vu_khong_lo_metric() {
        var res = client(port).get().uri("/actuator/prometheus")
                .retrieve().toBodilessEntity();

        assertThat(res.getStatusCode().is2xxSuccessful()).isFalse();
    }
```

**Lưu ý:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` cấp `@LocalServerPort`; cổng quản trị lấy bằng `@LocalManagementPort`. Cần đặt `management.server.port=0` trong test để Spring cấp cổng ngẫu nhiên.

- [ ] **Step 2: Chạy test để xác nhận nó fail**

Kỳ vọng: FAIL — chưa có registry, `/actuator/prometheus` chưa tồn tại ở đâu cả.

- [ ] **Step 3: Thêm registry và tách cổng quản trị**

`cinebook-api/pom.xml`:

```xml
        <!-- Autoconfiguration cua Prometheus nam trong spring-boot-micrometer-metrics (di
             kem spring-boot-starter-actuator), con registry thi phai tu khai. Da doi chieu:
             spring-boot-actuator-autoconfigure 4.1.0 khong con lop prometheus nao. -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
```

`application.yml`:

```yaml
management:
  server:
    # Cong quan tri tach rieng khoi cong nghiep vu. /actuator/prometheus ke ten endpoint,
    # so nguoi dung va nhip giao dich — khong duoc de no nam cung cho voi API cong khai.
    port: 8090
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

Worker dùng cổng 8091 (8081 đã là cổng nghiệp vụ của nó).

- [ ] **Step 4: Prometheus và Grafana trong compose**

`ops/prometheus/prometheus.yml` scrape hai đích: `host.docker.internal:8090` và `:8091`. **Không** scrape 8080/8081.

Grafana provisioning bằng file (datasource + dashboard JSON), không dựng dashboard bằng tay: một dashboard chỉ tồn tại trong volume của người tạo ra nó thì người khác clone repo về sẽ không thấy gì.

- [ ] **Step 5: Kiểm chứng bằng tay**

```bash
docker compose up -d
curl -s localhost:8090/actuator/prometheus | head
```

Mở `http://localhost:9090/targets` — cả hai đích phải **UP**. Đây là bước không được bỏ: pom khai đúng mà endpoint không bật là chuyện đã xảy ra ở Milestone 1 với Flyway.

- [ ] **Step 6: Ba thứ vỡ ra khi chạy thật, không cái nào test bắt trước được**

1. **`SecurityConfig` chặn luôn `/actuator/prometheus`.** Chuỗi chính đang `anyRequest().authenticated()`. Cách sửa: một `SecurityFilterChain` riêng `@Order(0)` khớp `EndpointRequest.toAnyEndpoint()` — với `management.server.port` tách riêng thì các endpoint đó chỉ tồn tại trên cổng quản trị, nên mở chúng không lộ gì trên 8080. Lớp `EndpointRequest` ở Boot 4 nằm tại `org.springframework.boot.security.autoconfigure.actuate.web.servlet` (đã tra trong jar, không đoán).
2. **Worker trả 403** cho `/actuator/prometheus` vì `WorkerSecurityConfig` đang `denyAll`. Prometheus không scrape được worker. Cùng cách sửa.
3. **Actuator biến mất khỏi cổng nghiệp vụ** — đúng thiết kế, nhưng đổi hợp đồng vận hành. `ApiApplicationTest` đỏ vì gọi health trên 8080; `README.md` cũng đang chỉ sai. Và `application.yml` ghim 8090 nghĩa là **mọi test đều giành một cổng cố định**, nên `AbstractApiTest`/`AbstractWorkerTest` phải đặt `management.server.port=0`.

**Grafana chết khi khởi động** với `Datasource provisioning error: data source not found`: thêm `uid` cố định vào một datasource đã tồn tại trong volume cũ. Đây không phải chuyện riêng của máy tôi — bất kỳ ai đã chạy bản trước rồi pull bản này về đều dính. Sửa bằng `deleteDatasources` trong file provisioning.

- [ ] **Step 7: Commit**

```bash
git commit -m "feat: xuat metric qua cong quan tri rieng"
```

---

## Task 2: Metric nghiệp vụ

**Files:**
- Create: `cinebook-api/src/main/java/com/cinebook/shared/metrics/BookingMetrics.java`
- Create: `cinebook-api/src/main/java/com/cinebook/shared/metrics/OutboxMetrics.java`
- Modify: `HoldSeatsUseCase`, `ConfirmBookingUseCase`, `SweepExpiredHoldsUseCase`
- Test: `cinebook-api/src/test/java/com/cinebook/observability/BookingMetricsTest.java`

**Interfaces:**
- `BookingMetrics.ghiNhanGiuGhe(String ketQua, Duration thoiGian)` — `ketQua` ∈ `thanh_cong | xung_dot | het_han`
- `OutboxMetrics` đăng ký `Gauge` đọc `SELECT count(*) FROM outbox_events WHERE published_at IS NULL`

Bốn metric đáng xem, và lý do từng cái:

| Metric | Trả lời câu hỏi |
|---|---|
| `cinebook_seat_hold_total{ket_qua="xung_dot"}` | Bao nhiêu phần trăm người bấm giữ ghế thì bị người nhanh tay hơn cướp mất? Đây là **chỉ số trải nghiệm**, không phải chỉ số máy |
| `cinebook_seat_hold_seconds` (Timer) | Đường nóng của cả dự án mất bao lâu, đo ở p95/p99 chứ không phải trung bình |
| `cinebook_outbox_pending` (Gauge) | Event nằm chờ bao lâu. Số này phình lên là dấu hiệu Kafka hoặc relay có vấn đề — trước khi người dùng kịp phàn nàn vì không nhận được email |
| `cinebook_sweeper_released_total` | Sweeper có thực sự làm việc không, và mỗi vòng nhả bao nhiêu ghế |

- [ ] **Step 1: Viết test**

```java
    @Test
    void giu_ghe_thanh_cong_lam_counter_nhich() {
        double truoc = demCounter("thanh_cong");

        hold(fixture.tokenA(), fixture.seatIds("A1"));

        assertThat(demCounter("thanh_cong")).isEqualTo(truoc + 1);
    }

    /**
     * Xung dot phai duoc dem RIENG. Gop chung vao "loi" thi mat dung cai tin hieu dang
     * gia nhat: trong mot buoi ban ve dong, ti le xung dot cho biet nguoi dung dang phai
     * tranh nhau den muc nao.
     */
    @Test
    void giu_trung_ghe_lam_counter_xung_dot_nhich() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        double truoc = demCounter("xung_dot");

        hold(fixture.tokenB(), fixture.seatIds("A1"));

        assertThat(demCounter("xung_dot")).isEqualTo(truoc + 1);
    }
```

- [ ] **Step 2: Cài đặt**

`BookingMetrics` bọc `MeterRegistry`; `HoldSeatsUseCase` gọi nó ở cả hai nhánh (thành công và `SeatsUnavailableException`). Đừng nhét `MeterRegistry` thẳng vào use-case — một lớp mỏng giữ tên metric ở một chỗ, đổi tên không phải đi sửa năm nơi.

**Cẩn thận với cardinality:** không bao giờ đặt `seatId`, `bookingId` hay `userId` làm tag. Mỗi giá trị tag là một chuỗi thời gian riêng trong Prometheus; gắn id vào là cách nhanh nhất để giết instance Prometheus.

- [ ] **Step 3: Kiểm chứng phá hỏng**

Tạm đảo tên hai nhãn `thanh_cong` và `xung_dot` cho nhau rồi chạy lại test. Cả hai phải đỏ. Nếu không, test đang đo sai thứ.

**Một Timer, không phải Timer + Counter.** `cinebook.seat.hold` cho ra cả `_seconds_count` (số lượt) lẫn `_seconds_bucket` (phân vị), nên không cần Counter riêng — thêm một Counter cùng tên là tạo ra hai nguồn sự thật để lệch nhau.

- [ ] **Step 3b: Suýt tắt `@Transactional` trên đường nóng**

Bản đầu tôi bọc lớp đo thời gian **bên trong** `HoldSeatsUseCase`: `hold()` gọi `giuGhe()` có `@Transactional`. Đó là **self-invocation** — proxy của Spring không chen vào, và transaction biến mất khỏi chính đường nóng của cả dự án. Không test nào trong bộ hiện tại chắc chắn bắt được ngay.

Chỗ đo đúng là **controller**: vừa tránh được bẫy đó, vừa tính cả thời gian commit — đoạn chậm nhất khi nhiều người cùng giành ghế. Đo từ bên trong transaction thì bỏ mất đúng phần đắt nhất.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: metric nghiep vu cho luong giu ghe va outbox"
```

---

## Task 3: Đo baseline bằng k6

**Files:**
- Create: `load-test/flash-sale.js`, `load-test/README.md`
- Create: `docs/ket-qua-do-tai.md`

- [ ] **Step 1: Kịch bản**

Flash sale, ba nhóm người dùng ảo chạy đồng thời trên **cùng một suất chiếu**:

| Nhóm | Hành vi | Tỉ lệ |
|---|---|---|
| Người xem | `GET /showtimes/{id}/seats` mỗi 2 giây | 80% |
| Người giành ghế | Đọc seat map rồi `POST /holds` một dải ghế ngẫu nhiên trong **cùng một vùng hẹp** | 15% |
| Người mua | Giữ ghế xong thì `POST /payments` | 5% |

Vùng ghế hẹp là có chủ ý: giành nhau mới là thứ đáng đo. Trải đều 96 ghế thì gần như không ai đụng ai và con số thu được sẽ đẹp một cách vô nghĩa.

Ngưỡng k6 (`thresholds`) đặt ngay từ đầu để lần chạy tự phán quyết đạt hay không:

```js
thresholds: {
  'http_req_duration{name:seatmap}': ['p(95)<300'],
  'http_req_failed{name:hold}': ['rate<0.01'],   // 409 KHONG tinh la loi
}
```

**409 không phải lỗi.** Trong kịch bản này, `SEATS_UNAVAILABLE` là kết quả nghiệp vụ đúng đắn — đánh dấu nó là lỗi thì mọi con số sau đó đều sai. Đây là chỗ dễ tự lừa mình nhất trong cả milestone.

- [ ] **Step 2: Chạy và ghi số**

```bash
docker run --rm -i --network host -v "$PWD/load-test:/scripts" grafana/k6:1.5.0 run /scripts/flash-sale.js
```

Ghi vào `docs/ket-qua-do-tai.md`: p50/p95/p99 của từng nhóm, tỉ lệ 409, throughput, **và điều kiện đo** (CPU/RAM máy, số VU, thời lượng, số ghế, dữ liệu demo bao nhiêu suất). Kèm ảnh chụp Grafana trong lúc chạy.

- [ ] **Step 3: Tìm điểm nghẽn**

Không đoán. Ba nguồn dữ liệu, đối chiếu với nhau:

1. Grafana: endpoint nào có p95 xấu nhất, và nó xấu vì CPU, vì chờ DB, hay vì connection pool cạn.
2. `EXPLAIN (ANALYZE, BUFFERS)` trên truy vấn của endpoint đó với dữ liệu thật.
3. `pg_stat_statements` nếu bật được: truy vấn nào chiếm nhiều thời gian nhất.

Viết kết luận vào `docs/ket-qua-do-tai.md` **trước khi** sửa bất cứ thứ gì. Nếu số liệu chỉ vào một chỗ khác với dự đoán (cache seat map), thì sửa chỗ số liệu chỉ, không sửa chỗ mình muốn.

- [ ] **Step 4: Commit**

```bash
git commit -m "test: kich ban k6 flash sale va so lieu baseline"
```

---

## Task 4: Tối ưu đúng thứ đã đo, rồi đo lại

**Files:** phụ thuộc kết quả Task 3.

**Giả thuyết đứng đầu** (spec mục 2.3): `GET /showtimes/{id}/seats` là điểm nghẽn, vì nó là endpoint bị gọi nhiều nhất và mỗi lần đều `JOIN` bốn bảng. Cách sửa dự kiến: cache trong Redis, **vô hiệu hoá bằng chính event đã có** — `SeatMapChannel` đang bắn mỗi khi ghế đổi trạng thái, nên chỗ đó cũng là chỗ xoá cache. Không cần TTL đoán mò.

Nhưng chỉ làm nếu Task 3 chỉ vào đó.

- [ ] **Step 1: Sửa một thứ duy nhất**

Một thay đổi mỗi lần đo. Sửa hai thứ rồi đo một lần thì không biết thứ nào có tác dụng — và nếu kết quả xấu đi cũng không biết vì cái nào.

- [ ] **Step 2: Test tính đúng đắn TRƯỚC khi đo lại**

Với cache: phải có test khẳng định **người khác giữ ghế thì lần đọc kế tiếp thấy ngay**, không phải chờ TTL. Một cache seat map trả dữ liệu cũ là cách bán hai lần một ghế — nhanh hơn mà sai thì tệ hơn chậm mà đúng.

Với Redis chết: seat map phải đọc thẳng Postgres (spec mục 7), có test.

- [ ] **Step 3: Đo lại đúng kịch bản cũ**

Cùng máy, cùng số VU, cùng thời lượng, cùng dữ liệu. Đổi điều kiện đo rồi so sánh là tự lừa mình.

- [ ] **Step 4: Viết README có biểu đồ**

Bảng trước/sau, biểu đồ p95, và **một câu giải thích vì sao nhanh hơn** — không chỉ "thêm cache". Nếu không giải thích được cơ chế thì con số chưa đáng tin.

Ghi cả những thứ **không** cải thiện, nếu có. Một milestone tối ưu mà mọi thứ đều đẹp lên thường là một milestone đo sai.

- [ ] **Step 5: Commit**

```bash
git commit -m "perf: toi uu duong doc so do ghe va do lai"
```

---

## Task 5: Trace và log có traceId

**Files:**
- Modify: `cinebook-api/pom.xml`, `cinebook-worker/pom.xml`
- Modify: `docker-compose.yml` — Jaeger
- Create: `logback-spring.xml` cho cả hai deployable

- [ ] **Step 1: Bridge và exporter**

```xml
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-otel</artifactId>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-exporter-otlp</artifactId>
        </dependency>
```

Autoconfiguration nằm trong `spring-boot-micrometer-tracing` (đã kiểm chứng có tồn tại ở 4.1.0). Sampling để `1.0` khi demo và ghi rõ rằng production phải hạ xuống.

- [ ] **Step 2: Log JSON có traceId**

Giá trị thật của bước này: một request lỗi trong log cho ra `traceId`, dán vào Jaeger là thấy toàn bộ đường đi — kể cả đoạn chạy ở worker. Không có nó thì việc điều tra là đọc log hai tiến trình rồi ghép bằng mắt theo dấu thời gian.

- [ ] **Step 3: Kiểm chứng bằng tay**

Đặt một vé qua UI, lấy `traceId` trong log, mở Jaeger và khẳng định thấy span của `POST /holds` kèm span truy vấn DB bên dưới.

**Câu hỏi cần trả lời trong lúc làm:** trace có nối được từ `cinebook-api` sang `cinebook-worker` qua Kafka không? Nếu không tự động thì ghi rõ vào README rằng context propagation qua Kafka chưa nối, thay vì để người đọc tự phát hiện.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: trace phan tan va traceId trong log"
```

---

## Định nghĩa hoàn thành cho Milestone 8

- [ ] `mvn -B clean verify` xanh (chạy khi compose đã tắt).
- [ ] CI trên GitHub Actions xanh.
- [ ] `docker compose up` là có Prometheus scrape được cả hai deployable, Grafana có dashboard **nạp từ file trong repo**.
- [ ] `/actuator/prometheus` **không** trả lời trên cổng nghiệp vụ, và có test giữ điều đó.
- [ ] Bốn metric nghiệp vụ chạy thật, nhìn thấy nhích trên Grafana trong lúc k6 chạy.
- [ ] `docs/ket-qua-do-tai.md` có số liệu **trước và sau** kèm đầy đủ điều kiện đo.
- [ ] Một tối ưu được chọn **từ số liệu**, kèm giải thích cơ chế vì sao nhanh hơn.
- [ ] Tối ưu đó có test tính đúng đắn, không chỉ test tốc độ.
- [ ] Một `traceId` trong log dẫn tới một trace hoàn chỉnh trong Jaeger.

## Những gì cố ý chưa làm

| Chưa làm | Lý do |
|---|---|
| Alerting (Alertmanager, PagerDuty) | Cần người trực mới có nghĩa. Dashboard đã đủ cho một dự án cá nhân |
| Rate limit và virtual waiting room | Bạn đã cân nhắc và **không chọn** hướng này từ đầu; thêm vào đây là mở rộng phạm vi ngoài thoả thuận |
| Load test phân tán nhiều máy | Một máy đủ để tìm điểm nghẽn của một dự án cá nhân. Con số tuyệt đối vốn đã phụ thuộc máy |
| Profiling sâu (async-profiler, JFR) | Chỉ dùng khi số liệu chỉ vào CPU của ứng dụng. Nếu Task 3 chỉ ra như vậy thì lúc đó mới thêm |
| Log tập trung (Loki/ELK) | Hai tiến trình thì `docker compose logs` vẫn đọc được. Thêm một hệ thống nữa chỉ để đọc log là không tương xứng |
