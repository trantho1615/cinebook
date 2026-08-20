package com.cinebook.observability;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsExposureTest extends AbstractApiTest {

    @Test
    void endpoint_prometheus_tra_ve_metric() {
        // Goi mot endpoint nghiep vu truoc: http_server_requests chi xuat hien sau khi co
        // request duoc ghi nhan, nen khang dinh no cung la khang dinh rang metric HTTP
        // thuc su duoc do chu khong chi la registry rong.
        goi(port, "/showtimes");

        String noiDung = goi(congQuanTri, "/actuator/prometheus").getBody();

        assertThat(noiDung)
                .contains("jvm_memory_used_bytes")
                .contains("http_server_requests");
    }

    /**
     * Nua de bi quen: cong NGHIEP VU khong duoc lo metric.
     *
     * /actuator/prometheus ke ten tung endpoint, so nguoi dung dang hoat dong va nhip giao
     * dich — du de bat ky ai dung lai buc tranh kinh doanh. No thuoc ve mang noi bo, khong
     * phai Internet.
     */
    @Test
    void cong_nghiep_vu_khong_lo_metric() {
        var res = goi(port, "/actuator/prometheus");

        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("cong nghiep vu tra %s cho /actuator/prometheus", res.getStatusCode())
                .isFalse();
    }

    @Test
    void health_van_tra_loi_tren_cong_quan_tri() {
        assertThat(goi(congQuanTri, "/actuator/health").getStatusCode().is2xxSuccessful()).isTrue();
    }

    private org.springframework.http.ResponseEntity<String> goi(int cong, String duongDan) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + cong)
                .defaultStatusHandler(status -> true, (req, res) -> {
                })
                .build()
                .get().uri(duongDan).retrieve().toEntity(String.class);
    }
}
