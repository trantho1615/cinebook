package com.cinebook.worker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Container dung chung cho ca lop con — khoi dong mot lan cho ca lan chay test.
 * Cung mau voi AbstractIntegrationTest ben cinebook-api.
 *
 * Flyway bat lai o day: worker chay that KHONG migrate (cinebook-api so huu schema),
 * nhung container test la DB trang nen phai co ai do dung schema len.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Giong main(): worker doc cinebook-worker.yml, khong doc application.yml
        "spring.config.name=cinebook-worker",
        "spring.flyway.enabled=true",
        // Cong quan tri ngau nhien: cinebook-worker.yml ghim 8091, ma test khong duoc gianh
        // cong voi worker that dang chay tren may.
        "management.server.port=0"
})
// Profile "test" tat lich chay job — xem SchedulingConfig.
@ActiveProfiles("test")
public abstract class AbstractWorkerTest {

    @LocalServerPort
    protected int port;

    @LocalManagementPort
    protected int congQuanTri;


    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine");

    // Cung image voi docker-compose.yml: khong test tren mot phien ban broker khac voi
    // phien ban chay that.
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void haTang(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
