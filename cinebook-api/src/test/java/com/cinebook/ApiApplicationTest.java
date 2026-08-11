package com.cinebook;

import com.cinebook.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ke thua AbstractIntegrationTest de dung container Postgres rieng.
 * Neu khong, test se ket noi vao Postgres cua docker compose tren may lap trinh vien
 * va do tren CI, noi khong co compose nao chay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiApplicationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void health_endpoint_bao_trang_thai_UP() {
        String body = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/actuator/health")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }
}
