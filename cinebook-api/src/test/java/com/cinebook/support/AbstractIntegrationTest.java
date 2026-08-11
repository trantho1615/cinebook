package com.cinebook.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Lop cha cho moi integration test. Container Postgres duoc khai bao static va
 * khoi dong mot lan duy nhat cho ca JVM test, nen cac test class dung chung mot
 * instance thay vi moi class khoi dong lai container.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }
}
