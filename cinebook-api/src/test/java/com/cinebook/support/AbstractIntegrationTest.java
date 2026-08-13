package com.cinebook.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate truncateTemplate;

    /**
     * Container dung chung ca phien test nen du lieu cua test truoc con lai.
     * Goi trong @BeforeEach de moi test bat dau tu trang thai sach.
     */
    protected void truncate(String... tables) {
        truncateTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
    }
}
