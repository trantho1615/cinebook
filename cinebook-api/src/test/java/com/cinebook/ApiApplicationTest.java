package com.cinebook;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ke thua AbstractApiTest de dung container Postgres rieng. Neu khong, test se ket noi vao
 * Postgres cua docker compose tren may lap trinh vien va do tren CI, noi khong co compose.
 *
 * Tu Milestone 8, health nam tren CONG QUAN TRI: actuator da tach khoi cong nghiep vu de
 * /actuator/prometheus khong nam cung cho voi API cong khai.
 */
class ApiApplicationTest extends AbstractApiTest {

    @Test
    void health_endpoint_bao_trang_thai_UP() {
        String body = RestClient.create()
                .get()
                .uri("http://localhost:" + congQuanTri + "/actuator/health")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }
}
