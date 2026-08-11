# Milestone 1 — Nền móng

**Goal:** Dựng bộ khung chạy được của cinebook: Maven multi-module hai deployable, hạ tầng local bằng Docker Compose, migration Flyway, integration test chạy trên Postgres thật, và hai luật kiến trúc được ép ở tầng build.

**Architecture:** Maven multi-module với parent POM kế thừa `spring-boot-starter-parent`. Ba module con: `common` (thư viện dùng chung), `cinebook-api` (deployable phục vụ HTTP), `cinebook-worker` (deployable chạy nền, không có endpoint nghiệp vụ). Ranh giới giữa các module nghiệp vụ và ranh giới "worker không phục vụ HTTP" đều được kiểm chứng bằng ArchUnit test, không dựa vào kỷ luật cá nhân.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Maven 3.9, PostgreSQL 16, Flyway, Testcontainers 1.21.3, ArchUnit 1.4.1, JUnit 5, AssertJ, GitHub Actions.

**Spec:** `docs/specs/2026-08-11-cinebook-core-design.md` — milestone này phủ mục 2.1, 2.2, 2.3, 3.3 và phần hạ tầng test của mục 8.1.

## Global Constraints

Mọi task đều phải tuân thủ những ràng buộc dưới đây.

- **Java version:** 21. Đặt qua property `<java.version>21</java.version>` trong parent POM.
- **Spring Boot:** 3.5.3, khai báo đúng một lần ở `<parent>` của parent POM.
- **Maven coordinates:** `groupId` = `com.cinebook`, parent `artifactId` = `cinebook`, `version` = `0.1.0-SNAPSHOT`.
- **Base package:** `com.cinebook`. Module nghiệp vụ nằm ở `com.cinebook.<module>` với `<module>` thuộc: `identity`, `catalog`, `booking`, `payment`, `notification`.
- **Cấu trúc trong mỗi module nghiệp vụ:** đúng bốn package con `api`, `domain`, `infra`, `web`. Chỉ `api` được module khác nhìn thấy.
- **Không dùng H2.** Mọi integration test chạy trên Postgres thật qua Testcontainers (spec mục 8.1).
- **Không đặt version trong POM con.** Version tập trung ở `<properties>` và `<dependencyManagement>` của parent.
- **Commit message viết không dấu**, theo Conventional Commits (`feat:`, `test:`, `chore:`, `docs:`, `ci:`). **Không thêm trailer đồng tác giả, không nhắc tới công cụ AI trong commit hay trong code.**
- **Mỗi task kết thúc bằng đúng một commit** và `mvn -B verify` phải xanh trước khi commit.

## File Structure

| File | Trách nhiệm | Task |
|---|---|---|
| `pom.xml` | Parent POM: version, module list, dependencyManagement | 1 |
| `common/pom.xml` | Module thư viện dùng chung | 1 |
| `common/src/main/java/com/cinebook/common/event/DomainEvent.java` | Contract của event trong outbox | 1 |
| `cinebook-api/pom.xml` | Deployable HTTP | 1 |
| `cinebook-api/src/main/java/com/cinebook/ApiApplication.java` | Entry point của api | 1 |
| `cinebook-api/src/main/resources/application.yml` | Cấu hình api | 1, 2 |
| `cinebook-api/src/test/java/com/cinebook/ApiApplicationTest.java` | Smoke test: app khởi động, health UP | 1 |
| `docker-compose.yml` | Postgres, Redis, Kafka cho môi trường local | 2 |
| `cinebook-api/src/main/resources/db/migration/V1__baseline.sql` | Migration đầu tiên: extension Postgres | 2 |
| `cinebook-api/src/test/java/com/cinebook/support/AbstractIntegrationTest.java` | Lớp cha cho mọi integration test, giữ container dùng chung | 2 |
| `cinebook-api/src/test/java/com/cinebook/db/BaselineMigrationTest.java` | Chứng minh Flyway chạy và extension đã cài | 2 |
| `cinebook-api/src/test/java/com/cinebook/architecture/ModuleBoundaryTest.java` | Ép luật phụ thuộc giữa module | 3 |
| `cinebook-api/src/test/java/com/cinebook/archfixture/**` | Fixture vi phạm có chủ ý, chứng minh luật thật sự bắt lỗi | 3 |
| `cinebook-worker/pom.xml` | Deployable chạy nền | 4 |
| `cinebook-worker/src/main/java/com/cinebook/worker/WorkerApplication.java` | Entry point của worker | 4 |
| `cinebook-worker/src/main/resources/application.yml` | Cấu hình worker | 4 |
| `cinebook-worker/src/test/java/com/cinebook/worker/WorkerApplicationTest.java` | Smoke test + luật "worker không có controller" | 4 |
| `.github/workflows/ci.yml` | Build và test mỗi push | 5 |
| `README.md` | Hướng dẫn chạy local | 5 |

---

## Task 1: Khung Maven multi-module và api khởi động được

**Files:**
- Create: `pom.xml`
- Create: `common/pom.xml`
- Create: `common/src/main/java/com/cinebook/common/event/DomainEvent.java`
- Create: `cinebook-api/pom.xml`
- Create: `cinebook-api/src/main/java/com/cinebook/ApiApplication.java`
- Create: `cinebook-api/src/main/resources/application.yml`
- Test: `cinebook-api/src/test/java/com/cinebook/ApiApplicationTest.java`

**Interfaces:**
- Consumes: không có (task đầu tiên).
- Produces:
  - Parent POM `com.cinebook:cinebook:0.1.0-SNAPSHOT` với `<modules>` mà task 4 sẽ thêm vào.
  - `com.cinebook:common:0.1.0-SNAPSHOT` đã được quản lý version trong `<dependencyManagement>`.
  - `public record DomainEvent(String aggregateType, String aggregateId, String eventType, String payload, Instant occurredAt)` — task của milestone sau dùng làm contract ghi vào bảng `outbox_events`.
  - `com.cinebook.ApiApplication` — lớp `@SpringBootApplication` mà mọi `@SpringBootTest` trong `cinebook-api` sẽ tìm thấy.

- [ ] **Step 1: Viết test khởi động**

Tạo `cinebook-api/src/test/java/com/cinebook/ApiApplicationTest.java`:

```java
package com.cinebook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiApplicationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void health_endpoint_bao_trang_thai_UP() {
        String body = rest.getForObject("/actuator/health", String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }
}
```

Test này khẳng định hai điều cùng lúc: Spring context nạp được, và actuator đã được bật đúng cấu hình. Nó mạnh hơn `contextLoads()` rỗng.

- [ ] **Step 2: Chạy test để xác nhận nó fail**

```bash
mvn -B -q test
```

Kỳ vọng: FAIL. Chưa có `pom.xml` nên Maven báo `The goal you specified requires a project to execute but there is no POM in this directory`.

- [ ] **Step 3: Tạo parent POM**

Tạo `pom.xml` ở thư mục gốc:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.3</version>
        <relativePath/>
    </parent>

    <groupId>com.cinebook</groupId>
    <artifactId>cinebook</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>cinebook</name>
    <description>He thong dat ve xem phim</description>

    <modules>
        <module>common</module>
        <module>cinebook-api</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <archunit.version>1.4.1</archunit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.cinebook</groupId>
                <artifactId>common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>${archunit.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

Lưu ý: `spring-boot-starter-parent` đã quản lý sẵn version của Testcontainers, Flyway, AssertJ, Awaitility. Không khai báo version cho những thứ đó ở bất kỳ đâu.

- [ ] **Step 4: Tạo module `common`**

Tạo `common/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.cinebook</groupId>
        <artifactId>cinebook</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>common</artifactId>
    <name>cinebook-common</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Module này **không** khai báo `spring-boot-maven-plugin` vì nó là thư viện, không phải ứng dụng chạy được.

Tạo `common/src/main/java/com/cinebook/common/event/DomainEvent.java`:

```java
package com.cinebook.common.event;

import java.time.Instant;

/**
 * Contract cua mot domain event truoc khi ghi vao bang outbox_events.
 * payload la chuoi JSON da serialize.
 */
public record DomainEvent(
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant occurredAt) {
}
```

- [ ] **Step 5: Tạo module `cinebook-api`**

Tạo `cinebook-api/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.cinebook</groupId>
        <artifactId>cinebook</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>cinebook-api</artifactId>
    <name>cinebook-api</name>

    <dependencies>
        <dependency>
            <groupId>com.cinebook</groupId>
            <artifactId>common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `cinebook-api/src/main/java/com/cinebook/ApiApplication.java`:

```java
package com.cinebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
```

Tạo `cinebook-api/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: cinebook-api

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 6: Chạy test để xác nhận nó pass**

```bash
mvn -B verify
```

Kỳ vọng: BUILD SUCCESS, `ApiApplicationTest` chạy 1 test và pass.

Nếu fail với `TestRestTemplate` không inject được: kiểm tra `webEnvironment = RANDOM_PORT` còn nguyên trên annotation `@SpringBootTest`.

- [ ] **Step 7: Chạy thử ứng dụng bằng tay**

```bash
mvn -B -pl cinebook-api spring-boot:run
```

Mở trình duyệt vào `http://localhost:8080/actuator/health`, phải thấy `{"status":"UP",...}`. Nhấn Ctrl+C để dừng.

Bước này không bắt buộc để test xanh, nhưng nó xác nhận artifact thật sự chạy được chứ không chỉ chạy trong test harness.

- [ ] **Step 8: Commit**

```bash
git add pom.xml common cinebook-api
git commit -m "feat: khung maven multi-module va api khoi dong duoc"
```

---

## Task 2: Postgres, Flyway và nền tảng integration test

**Files:**
- Create: `docker-compose.yml`
- Create: `cinebook-api/src/main/resources/db/migration/V1__baseline.sql`
- Create: `cinebook-api/src/test/java/com/cinebook/support/AbstractIntegrationTest.java`
- Modify: `cinebook-api/pom.xml` (thêm dependency JPA, Postgres driver, Flyway, Testcontainers)
- Modify: `cinebook-api/src/main/resources/application.yml` (thêm datasource, JPA, Flyway)
- Test: `cinebook-api/src/test/java/com/cinebook/db/BaselineMigrationTest.java`

**Interfaces:**
- Consumes: parent POM và module `cinebook-api` từ Task 1.
- Produces:
  - `public abstract class AbstractIntegrationTest` trong package `com.cinebook.support` — mọi integration test của các milestone sau **phải** kế thừa lớp này. Nó giữ một `PostgreSQLContainer` dùng chung cho toàn bộ JVM test, nên container chỉ khởi động một lần.
  - Thư mục `db/migration` với quy ước đặt tên `V<số>__<mô_tả>.sql`. Migration tiếp theo là `V2__identity.sql` (Milestone 2).
  - Extension `btree_gist` đã cài — điều kiện bắt buộc để tạo ràng buộc `EXCLUDE USING gist` trên bảng `showtimes` ở Milestone 3.

- [ ] **Step 1: Viết integration test cho migration**

Tạo `cinebook-api/src/test/java/com/cinebook/db/BaselineMigrationTest.java`:

```java
package com.cinebook.db;

import com.cinebook.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineMigrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flyway_da_chay_it_nhat_mot_migration_thanh_cong() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void extension_btree_gist_da_duoc_cai() {
        Integer installed = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'",
                Integer.class);

        assertThat(installed).isEqualTo(1);
    }
}
```

Test thứ hai mới là test có giá trị: `btree_gist` là điều kiện để tạo ràng buộc chống trùng lịch chiếu ở Milestone 3. Không có nó thì migration của Milestone 3 sẽ fail, và fail muộn thì khó tìm hơn nhiều.

- [ ] **Step 2: Chạy test để xác nhận nó fail**

```bash
mvn -B -pl cinebook-api test -Dtest=BaselineMigrationTest
```

Kỳ vọng: FAIL với lỗi biên dịch — `package com.cinebook.support does not exist`.

- [ ] **Step 3: Thêm dependency vào `cinebook-api/pom.xml`**

Chèn vào trong khối `<dependencies>` đang có:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
```

`flyway-database-postgresql` là artifact riêng từ Flyway 10 trở đi — thiếu nó thì Flyway báo `Unsupported Database: PostgreSQL`.

- [ ] **Step 4: Tạo migration baseline**

Tạo `cinebook-api/src/main/resources/db/migration/V1__baseline.sql`:

```sql
-- btree_gist cho phep dung toan tu dang thuc (=) trong rang buoc EXCLUDE,
-- can thiet cho rang buoc chong trung lich chieu trong cung mot phong (spec muc 5.7).
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

- [ ] **Step 5: Tạo lớp cha cho integration test**

Tạo `cinebook-api/src/test/java/com/cinebook/support/AbstractIntegrationTest.java`:

```java
package com.cinebook.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Lop cha cho moi integration test. Container Postgres duoc khai bao static va
 * khoi dong mot lan duy nhat cho ca JVM test, nen cac test class dung chung mot
 * instance thay vi moi class khoi dong lai container.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
```

Không dùng annotation `@Testcontainers` kèm `@Container` ở đây. Cặp annotation đó sẽ dừng và khởi động lại container cho **mỗi** test class, khiến bộ test chậm dần theo số lượng class. Cách khởi động trong khối `static` giữ container sống suốt phiên test.

`@ServiceConnection` tự động trỏ `spring.datasource.*` vào container, nên không cần `@DynamicPropertySource`.

- [ ] **Step 6: Cấu hình datasource và Flyway**

Thay toàn bộ nội dung `cinebook-api/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: cinebook-api

  datasource:
    url: jdbc:postgresql://localhost:5432/cinebook
    username: cinebook
    password: cinebook

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC

  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

Ba lựa chọn cần hiểu rõ:

- `ddl-auto: validate` — Hibernate **không bao giờ** tự sửa schema. Schema chỉ do Flyway quản lý. Nếu entity lệch với bảng thật thì app không khởi động được, và đó là hành vi mong muốn.
- `open-in-view: false` — tắt Open Session In View. Để bật thì session Hibernate sống tới tận lúc render response, gây giữ connection lâu và query lười phát sinh ngoài tầm kiểm soát của transaction.
- `hibernate.jdbc.time_zone: UTC` — mọi `timestamptz` đọc/ghi ở UTC, khớp quy ước ở spec mục 5.1.

- [ ] **Step 7: Chạy test để xác nhận nó pass**

```bash
mvn -B -pl cinebook-api test -Dtest=BaselineMigrationTest
```

Kỳ vọng: PASS, 2 test.

Lần chạy đầu sẽ mất khoảng 30-60 giây vì Docker phải kéo image `postgres:16-alpine`. Nếu lỗi `Could not find a valid Docker environment`, kiểm tra Docker Desktop đã chạy chưa.

- [ ] **Step 8: Tạo `docker-compose.yml` cho môi trường local**

Tạo `docker-compose.yml` ở thư mục gốc:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: cinebook-postgres
    environment:
      POSTGRES_DB: cinebook
      POSTGRES_USER: cinebook
      POSTGRES_PASSWORD: cinebook
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cinebook -d cinebook"]
      interval: 5s
      timeout: 3s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: cinebook-redis
    command: ["redis-server", "--appendonly", "yes"]
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10

  kafka:
    image: apache/kafka:3.9.0
    container_name: cinebook-kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0

volumes:
  pgdata:
  redisdata:
```

Redis và Kafka chưa được dùng ở milestone này. Chúng có mặt sẵn để các milestone sau không phải sửa file compose. Prometheus, Grafana và Jaeger sẽ được thêm ở milestone observability, không thêm bây giờ.

- [ ] **Step 9: Kiểm tra compose chạy được và app kết nối được**

```bash
docker compose up -d
docker compose ps
```

Kỳ vọng: cả ba service ở trạng thái `running`, postgres và redis `healthy`.

```bash
mvn -B -pl cinebook-api spring-boot:run
```

Kỳ vọng: log hiện `Successfully applied 1 migration to schema "public"`. Truy cập `http://localhost:8080/actuator/health` thấy `UP`. Ctrl+C để dừng.

```bash
docker compose down
```

- [ ] **Step 10: Chạy toàn bộ test và commit**

```bash
mvn -B verify
```

Kỳ vọng: BUILD SUCCESS, 3 test pass (1 của Task 1, 2 của task này).

```bash
git add docker-compose.yml cinebook-api
git commit -m "feat: postgres, flyway va nen tang integration test bang testcontainers"
```

---

## Task 3: Ép ranh giới module bằng ArchUnit

**Files:**
- Modify: `cinebook-api/pom.xml` (thêm dependency ArchUnit)
- Create: `cinebook-api/src/test/java/com/cinebook/archfixture/booking/domain/Seat.java`
- Create: `cinebook-api/src/test/java/com/cinebook/archfixture/payment/PaymentReachesIntoBookingDomain.java`
- Test: `cinebook-api/src/test/java/com/cinebook/architecture/ModuleBoundaryTest.java`

**Interfaces:**
- Consumes: parent POM (đã có `archunit-junit5` trong `dependencyManagement` từ Task 1).
- Produces:
  - `ModuleBoundaryTest` với hằng số `MODULES` liệt kê 5 module nghiệp vụ. Mỗi milestone sau khi thêm module mới **không** cần sửa test này — danh sách đã đầy đủ ngay từ đầu.
  - Luật được ép: class nằm ngoài `com.cinebook.<module>` không được phụ thuộc vào `com.cinebook.<module>.domain`, `.infra`, hoặc `.web`.

Milestone này chưa có code nghiệp vụ nào, nên luật sẽ đúng một cách rỗng. Vì vậy task có thêm một test thứ hai chạy luật lên một fixture vi phạm có chủ ý, để chứng minh luật thật sự bắt lỗi chứ không phải luôn xanh vì không có gì để kiểm tra.

- [ ] **Step 1: Viết test ranh giới module và test chứng minh luật có hiệu lực**

Tạo `cinebook-api/src/test/java/com/cinebook/architecture/ModuleBoundaryTest.java`:

```java
package com.cinebook.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleBoundaryTest {

    private static final String[] MODULES = {
            "identity", "catalog", "booking", "payment", "notification"
    };

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.cinebook");

    @Test
    void module_chi_duoc_phu_thuoc_vao_package_api_cua_module_khac() {
        for (String module : MODULES) {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("com.cinebook." + module + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.cinebook." + module + ".domain..",
                            "com.cinebook." + module + ".infra..",
                            "com.cinebook." + module + ".web..")
                    .because("chi package com.cinebook." + module + ".api duoc phep lo ra ngoai");

            rule.allowEmptyShould(true).check(productionClasses);
        }
    }

    @Test
    void luat_ranh_gioi_that_su_bat_loi_khi_co_vi_pham() {
        JavaClasses fixture = new ClassFileImporter()
                .importPackages("com.cinebook.archfixture");

        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.cinebook.archfixture.booking..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.cinebook.archfixture.booking.domain..");

        assertThatThrownBy(() -> rule.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PaymentReachesIntoBookingDomain");
    }
}
```

`allowEmptyShould(true)` là bắt buộc ở test thứ nhất: từ ArchUnit 1.0, một luật không khớp class nào sẽ **fail** thay vì pass. Ở milestone này chưa có class nghiệp vụ nào nên phải cho phép tập rỗng. Test thứ hai chính là thứ bù lại cho sự nới lỏng đó.

- [ ] **Step 2: Chạy test để xác nhận nó fail**

```bash
mvn -B -pl cinebook-api test -Dtest=ModuleBoundaryTest
```

Kỳ vọng: FAIL với lỗi biên dịch — `package com.tngtech.archunit.core.domain does not exist`.

- [ ] **Step 3: Thêm dependency ArchUnit**

Chèn vào khối `<dependencies>` của `cinebook-api/pom.xml`:

```xml
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
        </dependency>
```

Không ghi `<version>` và không ghi `<scope>` — cả hai đã được khai báo trong `dependencyManagement` của parent POM ở Task 1.

- [ ] **Step 4: Tạo fixture vi phạm có chủ ý**

Tạo `cinebook-api/src/test/java/com/cinebook/archfixture/booking/domain/Seat.java`:

```java
package com.cinebook.archfixture.booking.domain;

/**
 * Fixture cho ModuleBoundaryTest. Dai dien cho mot lop domain noi bo cua module booking.
 * Chi ton tai trong test scope, khong duoc dong goi vao artifact.
 */
public class Seat {
}
```

Tạo `cinebook-api/src/test/java/com/cinebook/archfixture/payment/PaymentReachesIntoBookingDomain.java`:

```java
package com.cinebook.archfixture.payment;

import com.cinebook.archfixture.booking.domain.Seat;

/**
 * Fixture vi pham co chu y: module payment cham thang vao domain cua module booking.
 * ModuleBoundaryTest dung lop nay de chung minh luat ranh gioi that su bat loi.
 */
public class PaymentReachesIntoBookingDomain {

    @SuppressWarnings("unused")
    private Seat seat;
}
```

- [ ] **Step 5: Chạy test để xác nhận nó pass**

```bash
mvn -B -pl cinebook-api test -Dtest=ModuleBoundaryTest
```

Kỳ vọng: PASS, 2 test.

Nếu test thứ hai fail với `Expecting code to raise a throwable`: kiểm tra hai file fixture đã nằm đúng dưới `src/test/java` và đã được biên dịch (chạy `mvn -B -pl cinebook-api test-compile` rồi kiểm tra `cinebook-api/target/test-classes/com/cinebook/archfixture`).

- [ ] **Step 6: Chạy toàn bộ test và commit**

```bash
mvn -B verify
```

Kỳ vọng: BUILD SUCCESS, 5 test pass.

```bash
git add cinebook-api
git commit -m "test: ep ranh gioi module bang archunit"
```

---

## Task 4: Deployable `cinebook-worker`

**Files:**
- Modify: `pom.xml` (thêm `cinebook-worker` vào `<modules>`)
- Create: `cinebook-worker/pom.xml`
- Create: `cinebook-worker/src/main/java/com/cinebook/worker/WorkerApplication.java`
- Create: `cinebook-worker/src/main/resources/application.yml`
- Test: `cinebook-worker/src/test/java/com/cinebook/worker/WorkerApplicationTest.java`

**Interfaces:**
- Consumes: parent POM từ Task 1; module `common` từ Task 1.
- Produces:
  - `com.cinebook.worker.WorkerApplication` — entry point mà các milestone sau gắn Kafka consumer và scheduled job vào.
  - Luật ArchUnit "worker không có controller", biến câu ràng buộc ở spec mục 2.1 thành thứ build kiểm tra được.

Worker chạy trên cổng 8081 và chỉ lộ actuator. Nó **không** có endpoint nghiệp vụ nào — đó là điều kiện để khẳng định "worker chết thì hệ thống vẫn bán được vé" ở spec mục 2.1.

- [ ] **Step 1: Viết test cho worker**

Tạo `cinebook-worker/src/test/java/com/cinebook/worker/WorkerApplicationTest.java`:

```java
package com.cinebook.worker;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@SpringBootTest
class WorkerApplicationTest {

    @Test
    void spring_context_nap_duoc() {
        // Test pass khi @SpringBootTest nap context thanh cong.
        // Cac milestone sau se thay bang assertion tren scheduled job cu the.
    }

    @Test
    void worker_khong_khai_bao_endpoint_http_nghiep_vu() {
        JavaClasses workerClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cinebook.worker");

        ArchRule rule = noClasses()
                .should().beAnnotatedWith(RestController.class)
                .orShould().beAnnotatedWith(Controller.class)
                .because("cinebook-worker chi tieu thu Kafka va chay scheduled job (spec muc 2.1)");

        rule.allowEmptyShould(true).check(workerClasses);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận nó fail**

```bash
mvn -B -pl cinebook-worker test
```

Kỳ vọng: FAIL với `Could not find the selected project in the reactor: cinebook-worker`.

- [ ] **Step 3: Đăng ký module trong parent POM**

Sửa khối `<modules>` trong `pom.xml` ở thư mục gốc thành:

```xml
    <modules>
        <module>common</module>
        <module>cinebook-api</module>
        <module>cinebook-worker</module>
    </modules>
```

- [ ] **Step 4: Tạo module worker**

Tạo `cinebook-worker/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.cinebook</groupId>
        <artifactId>cinebook</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>cinebook-worker</artifactId>
    <name>cinebook-worker</name>

    <dependencies>
        <dependency>
            <groupId>com.cinebook</groupId>
            <artifactId>common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-boot-starter-web` có mặt chỉ để phục vụ actuator trên cổng quản trị. Luật ArchUnit ở Step 1 đảm bảo nó không bị dùng để thêm endpoint nghiệp vụ.

Tạo `cinebook-worker/src/main/java/com/cinebook/worker/WorkerApplication.java`:

```java
package com.cinebook.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
```

Tạo `cinebook-worker/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: cinebook-worker

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

Worker chưa cần datasource ở milestone này. Nó sẽ được thêm cùng với sweeper và outbox relay ở milestone tương ứng.

- [ ] **Step 5: Chạy test để xác nhận nó pass**

```bash
mvn -B -pl cinebook-worker test
```

Kỳ vọng: PASS, 2 test.

- [ ] **Step 6: Xác nhận hai deployable chạy song song được**

Mở hai terminal:

```bash
mvn -B -pl cinebook-api spring-boot:run
```

```bash
mvn -B -pl cinebook-worker spring-boot:run
```

Kỳ vọng: `http://localhost:8080/actuator/health` và `http://localhost:8081/actuator/health` đều trả `UP`, không có xung đột cổng. Ctrl+C cả hai.

- [ ] **Step 7: Chạy toàn bộ test và commit**

```bash
mvn -B verify
```

Kỳ vọng: BUILD SUCCESS, 7 test pass.

```bash
git add pom.xml cinebook-worker
git commit -m "feat: deployable cinebook-worker khong phuc vu http nghiep vu"
```

---

## Task 5: CI trên GitHub Actions và README

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `README.md`

**Interfaces:**
- Consumes: toàn bộ cây Maven từ Task 1-4. CI chạy đúng một lệnh `mvn -B verify` — cùng lệnh mà bạn chạy ở máy.
- Produces: badge trạng thái build trong README; mọi milestone sau chỉ cần giữ `mvn -B verify` xanh, không phải sửa CI.

- [ ] **Step 1: Xác nhận lệnh CI sẽ chạy đang xanh ở máy**

```bash
mvn -B clean verify
```

Kỳ vọng: BUILD SUCCESS, 7 test pass. Nếu bước này đỏ thì CI cũng sẽ đỏ — sửa trước khi đi tiếp.

- [ ] **Step 2: Viết workflow**

Tạo `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build and test
        run: mvn -B clean verify

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: '**/target/surefire-reports/*.xml'
          if-no-files-found: ignore
```

Runner `ubuntu-latest` có sẵn Docker daemon, nên Testcontainers chạy được mà không cần cấu hình thêm.

`if: always()` ở bước cuối để báo cáo test vẫn được tải lên khi build đỏ — đó là lúc bạn cần chúng nhất.

- [ ] **Step 3: Viết README**

Tạo `README.md`:

```markdown
# cinebook

He thong dat ve xem phim. Backend Java 21 + Spring Boot, tap trung vao cac bai toan
tranh chap ghe dong thoi, thanh toan bat dong bo va observability.

Thiet ke chi tiet: [docs/specs/2026-08-11-cinebook-core-design.md](docs/specs/2026-08-11-cinebook-core-design.md)

## Yeu cau

- JDK 21
- Maven 3.9+
- Docker + Docker Compose

## Chay local

```bash
docker compose up -d
mvn -B -pl cinebook-api spring-boot:run     # cong 8080
mvn -B -pl cinebook-worker spring-boot:run  # cong 8081
```

Health check: http://localhost:8080/actuator/health

## Chay test

```bash
mvn -B verify
```

Integration test chay tren Postgres that qua Testcontainers, khong dung H2.
Lan chay dau se mat them thoi gian de keo image `postgres:16-alpine`.

## Cau truc

| Module | Vai tro |
|---|---|
| `common` | Contract dung chung giua cac deployable |
| `cinebook-api` | Phuc vu REST va WebSocket, cong 8080 |
| `cinebook-worker` | Scheduled job va Kafka consumer, cong 8081, khong co endpoint nghiep vu |
```

- [ ] **Step 4: Tạo repository trên GitHub và đẩy code**

Bước này cần tài khoản GitHub của bạn. Nếu chưa đăng nhập `gh`, chạy lệnh sau **trong terminal của bạn** (nó cần tương tác):

```bash
gh auth login
```

Sau đó:

```bash
gh repo create cinebook --public --source=. --remote=origin --push
```

Nếu muốn để repo ở chế độ riêng tư trước, đổi `--public` thành `--private`.

- [ ] **Step 5: Xác nhận CI xanh**

```bash
gh run watch
```

Kỳ vọng: workflow `CI` kết thúc với trạng thái `success`.

Nếu đỏ ở bước `Build and test`, tải log về xem:

```bash
gh run view --log-failed
```

- [ ] **Step 6: Thêm badge và commit**

Thêm dòng sau ngay dưới tiêu đề `# cinebook` trong `README.md`, thay `<user>` bằng tên tài khoản GitHub của bạn:

```markdown
![CI](https://github.com/<user>/cinebook/actions/workflows/ci.yml/badge.svg)
```

```bash
git add .github README.md
git commit -m "ci: build va test tren github actions"
git push
```

---

## Định nghĩa hoàn thành cho Milestone 1

Milestone được coi là xong khi tất cả những điều sau đúng:

- [ ] `mvn -B clean verify` xanh, 7 test pass.
- [ ] `docker compose up -d` dựng được postgres, redis, kafka; postgres và redis báo `healthy`.
- [ ] `cinebook-api` chạy ở cổng 8080 và `cinebook-worker` chạy ở cổng 8081, cả hai trả `UP` ở `/actuator/health`.
- [ ] Flyway đã áp dụng `V1__baseline.sql`, extension `btree_gist` có trong `pg_extension`.
- [ ] `ModuleBoundaryTest` chứng minh được luật ranh giới bắt lỗi trên fixture vi phạm.
- [ ] CI trên GitHub Actions xanh, badge hiện trong README.
- [ ] Lịch sử git có 5 commit, mỗi task một commit, message không dấu.

## Những gì cố ý chưa làm ở milestone này

Để tránh làm việc thừa và để mỗi milestone có ranh giới rõ:

| Chưa làm | Sẽ làm ở |
|---|---|
| Package của 5 module nghiệp vụ | Milestone tương ứng của từng module |
| Redis config trong ứng dụng | Milestone 2 (lưu refresh token) |
| Kafka producer/consumer | Milestone payment và worker |
| Datasource cho worker | Milestone worker (sweeper, outbox relay) |
| Prometheus, Grafana, Jaeger trong compose | Milestone observability |
| Spring Security | Milestone 2 |
| Thư mục `web/` (UI) | Milestone UI — là app JS, không phải module Maven |
| Thư mục `load-test/` (k6) | Milestone observability — là script k6, không phải module Maven |
