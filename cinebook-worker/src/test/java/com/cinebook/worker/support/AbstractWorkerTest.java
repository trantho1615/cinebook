package com.cinebook.worker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Container dung chung cho ca lop con — khoi dong mot lan cho ca lan chay test.
 * Cung mau voi AbstractIntegrationTest ben cinebook-api.
 *
 * Flyway bat lai o day: worker chay that KHONG migrate (cinebook-api so huu schema),
 * nhung container test la DB trang nen phai co ai do dung schema len.
 */
@SpringBootTest(properties = "spring.flyway.enabled=true")
// Profile "test" tat lich chay job — xem SchedulingConfig.
@ActiveProfiles("test")
public abstract class AbstractWorkerTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
