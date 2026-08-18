package com.cinebook.booking;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HoldSeatsTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void giu_ghe_thanh_cong_tra_ve_ma_dat_ve_va_tong_tien_dung() {
        var response = hold(fixture.tokenA(), fixture.seatIds("A1", "A2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body.get("code").asText()).isNotBlank();
        // A1 va A2 la hang STANDARD: 90000 moi ghe, khong phu phi
        assertThat(body.get("totalAmount").asLong()).isEqualTo(180000L);
        assertThat(body.get("status").asText()).isEqualTo("PENDING");

        Integer soHold = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class);
        assertThat(soHold).isEqualTo(2);
    }

    @Test
    void ghe_vip_va_couple_duoc_tinh_dung_phu_phi() {
        // C1 la VIP (+20000), E1 la COUPLE (+50000)
        var response = hold(fixture.tokenA(), fixture.seatIds("C1", "E1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("totalAmount").asLong())
                .isEqualTo((90000L + 20000L) + (90000L + 50000L));
    }

    @Test
    void ghe_da_bi_nguoi_khac_giu_thi_tra_409_kem_dung_danh_sach_ghe_mat() {
        hold(fixture.tokenA(), fixture.seatIds("A1", "A2"));

        // Nguoi B xin A2 (da bi giu) va A3 (con trong)
        var response = hold(fixture.tokenB(), fixture.seatIds("A2", "A3"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("SEATS_UNAVAILABLE");
        assertThat(response.getBody().get("message").asText()).contains("A2").doesNotContain("A3");
    }

    @Test
    void giu_ghe_la_all_or_nothing_khong_co_chuyen_giu_duoc_mot_phan() {
        hold(fixture.tokenA(), fixture.seatIds("A2"));

        hold(fixture.tokenB(), fixture.seatIds("A1", "A2", "A3"));

        // Nguoi B khong duoc giu A1 va A3 du chung con trong
        Integer soHoldCuaB = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE user_id = ?::uuid AND status = 'HELD'",
                Integer.class, fixture.userIdB());
        assertThat(soHoldCuaB).isZero();

        Integer soBooking = db.queryForObject("SELECT count(*) FROM bookings", Integer.class);
        assertThat(soBooking).isEqualTo(1);
    }

    @Test
    void xin_qua_8_ghe_bi_tu_choi_422() {
        var response = hold(fixture.tokenA(),
                fixture.seatIds("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(db.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isZero();
    }

    private ResponseEntity<JsonNode> hold(String token, List<String> seatIds) {
        return client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", seatIds))
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
