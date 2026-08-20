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
 *
 * Tu Milestone 8, actuator nam tren cong quan tri rieng, nen cac khang dinh ve giam sat
 * chuyen sang cong do.
 */
class WorkerSecurityTest extends AbstractWorkerTest {

    @Test
    void endpoint_giam_sat_voi_toi_duoc_tren_cong_quan_tri() {
        assertThat(goi(congQuanTri, "/actuator/health")).isEqualTo(HttpStatus.OK);
        assertThat(goi(congQuanTri, "/actuator/info")).isEqualTo(HttpStatus.OK);
    }

    /**
     * Da gap khi chay that: chuoi denyAll cua worker chan luon /actuator/prometheus va
     * Prometheus khong scrape duoc worker (403).
     */
    @Test
    void prometheus_scrape_duoc_worker() {
        assertThat(goi(congQuanTri, "/actuator/prometheus")).isEqualTo(HttpStatus.OK);
    }

    @Test
    void moi_duong_khac_deu_bi_tu_choi() {
        assertThat(goi(port, "/showtimes")).isNotEqualTo(HttpStatus.OK);
        assertThat(goi(port, "/auth/login")).isNotEqualTo(HttpStatus.OK);
        // Metric khong duoc lo ra cong nghiep vu.
        assertThat(goi(port, "/actuator/prometheus")).isNotEqualTo(HttpStatus.OK);
    }

    private HttpStatus goi(int cong, String path) {
        return (HttpStatus) RestClient.builder()
                .baseUrl("http://localhost:" + cong)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build()
                .get().uri(path).retrieve().toBodilessEntity().getStatusCode();
    }
}
