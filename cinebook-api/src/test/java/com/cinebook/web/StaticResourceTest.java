package com.cinebook.web;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class StaticResourceTest extends AbstractApiTest {

    @Test
    void trang_chu_tai_duoc_ma_khong_can_dang_nhap() {
        var response = client().get().uri("/").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("cinebook");
    }

    @Test
    void css_tai_duoc() {
        assertThat(client().get().uri("/css/app.css").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Nua thu hai cua thay doi trong SecurityConfig, va la nua de bi quen: mo file tinh
     * KHONG duoc lam mem cac endpoint nghiep vu.
     */
    @Test
    void mo_file_tinh_khong_lam_lo_endpoint_nghiep_vu() {
        assertThat(client().get().uri("/bookings").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(client().get().uri("/admin/users").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
