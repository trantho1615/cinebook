package com.cinebook;

import com.cinebook.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ke thua AbstractIntegrationTest de dung container Postgres rieng.
 * Neu khong, test se ket noi vao Postgres cua docker compose tren may lap trinh vien
 * va do tren CI, noi khong co compose nao chay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiApplicationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void health_endpoint_bao_trang_thai_UP() {
        String body = rest.getForObject("/actuator/health", String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }
}
