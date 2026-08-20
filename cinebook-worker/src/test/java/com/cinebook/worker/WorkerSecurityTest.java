package com.cinebook.worker;

import com.cinebook.worker.support.AbstractWorkerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Da phat hien khi chay jar that, khong phai khi chay test: worker chay voi cau hinh
 * security mac dinh cua Spring Boot, sinh mat khau ngau nhien va tra 401 cho
 * /actuator/info. Test nay giu cho dieu do khong quay lai.
 */
class WorkerSecurityTest extends AbstractWorkerTest {

    @Test
    void endpoint_giam_sat_voi_toi_duoc() {
        assertThat(goi("/actuator/health")).isEqualTo(HttpStatus.OK);
        assertThat(goi("/actuator/info")).isEqualTo(HttpStatus.OK);
    }

    @Test
    void moi_duong_khac_deu_bi_tu_choi() {
        assertThat(goi("/showtimes")).isNotEqualTo(HttpStatus.OK);
        assertThat(goi("/auth/login")).isNotEqualTo(HttpStatus.OK);
        assertThat(goi("/actuator/env")).isNotEqualTo(HttpStatus.OK);
    }

    private HttpStatus goi(String path) {
        return (HttpStatus) RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build()
                .get().uri(path).retrieve().toBodilessEntity().getStatusCode();
    }
}
