package com.cinebook.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Lop cha cho moi integration test. Hai container duoc khai bao static va khoi dong
 * mot lan duy nhat cho ca JVM test, nen cac test class dung chung mot instance
 * thay vi moi class khoi dong lai container.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine");

    // Testcontainers 2.x khong con module rieng cho Redis, nen dung GenericContainer.
    // Tham so name = "redis" la thu giup Spring Boot biet day la Redis de tu dien
    // spring.data.redis.host va .port.
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
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
