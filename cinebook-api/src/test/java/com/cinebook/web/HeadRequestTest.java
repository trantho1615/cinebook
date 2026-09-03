package com.cinebook.web;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HEAD phai tra loi giong het GET, tru phan than (RFC 9110 muc 9.3.2).
 *
 * SecurityConfig chi liet ke HttpMethod.GET cho cac duong dan cong khai, nen HEAD roi xuong
 * anyRequest().authenticated() va an 401. Hau qua that: cac dich vu giam sat uptime
 * (UptimeRobot, Pingdom, StatusCake) mac dinh gui HEAD chu khong phai GET, nen chung bao
 * trang chet trong khi nguoi dung that van vao duoc binh thuong.
 *
 * Phat hien luc kiem chung ban deploy that bang `curl -I` — lenh do gui HEAD, va 401 tra ve
 * suyt bi doc nham thanh loi deploy.
 *
 * Lop nay dung rieng thay vi nhet vao StaticResourceTest vi bat bien nay khong chi thuoc ve
 * file tinh: no ap cho MOI duong dan da mo cho khach vang lai. Go mot dong HttpMethod.HEAD
 * trong SecurityConfig la mot test o day do ngay.
 */
class HeadRequestTest extends AbstractApiTest {

    @Test
    void file_tinh_tra_loi_HEAD() {
        assertThat(client().head().uri("/").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client().head().uri("/css/app.css").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void endpoint_duyet_cong_khai_tra_loi_HEAD() {
        assertThat(client().head().uri("/showtimes").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client().head().uri("/movies").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client().head().uri("/cinemas").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Nua de bi quen: mo them mot phuong thuc cho duong dan cong khai KHONG duoc mo no cho
     * duong dan rieng tu. Thieu test nay thi mot dau sao dat nham cho van xanh het.
     */
    @Test
    void HEAD_khong_lam_lo_endpoint_can_dang_nhap() {
        assertThat(client().head().uri("/bookings").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(client().head().uri("/admin/users").retrieve()
                .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
