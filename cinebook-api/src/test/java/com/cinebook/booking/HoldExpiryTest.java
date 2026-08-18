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

class HoldExpiryTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void hold_het_han_thi_nguoi_khac_lay_duoc_ghe_do() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        lamChoHetHan();

        var response = hold(fixture.tokenB(), fixture.seatIds("A1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void lich_su_hold_cu_van_duoc_giu_lai_voi_ly_do_taken_over() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        lamChoHetHan();
        hold(fixture.tokenB(), fixture.seatIds("A1"));

        // Mot dong EXPIRED (cua A) va mot dong HELD (cua B)
        Integer soExpired = db.queryForObject("""
                SELECT count(*) FROM seat_hold
                 WHERE status = 'EXPIRED' AND release_reason = 'TAKEN_OVER'
                """, Integer.class);
        assertThat(soExpired).isEqualTo(1);

        Integer soHeld = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class);
        assertThat(soHeld).isEqualTo(1);
    }

    @Test
    void hold_chua_het_han_thi_nguoi_khac_van_khong_lay_duoc() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));

        var response = hold(fixture.tokenB(), fixture.seatIds("A1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nguoi_dung_huy_don_thi_ghe_duoc_giai_phong_ngay() {
        String bookingId = hold(fixture.tokenA(), fixture.seatIds("A1", "A2"))
                .getBody().get("bookingId").asText();

        var huy = client().delete()
                .uri("/bookings/" + bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.tokenA())
                .retrieve()
                .toBodilessEntity();
        assertThat(huy.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer soHeld = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class);
        assertThat(soHeld).isZero();

        Integer soUserCancelled = db.queryForObject("""
                SELECT count(*) FROM seat_hold WHERE release_reason = 'USER_CANCELLED'
                """, Integer.class);
        assertThat(soUserCancelled).isEqualTo(2);

        // Va nguoi khac lay duoc ngay
        assertThat(hold(fixture.tokenB(), fixture.seatIds("A1")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void khong_huy_duoc_don_cua_nguoi_khac() {
        String bookingId = hold(fixture.tokenA(), fixture.seatIds("A1"))
                .getBody().get("bookingId").asText();

        var response = client().delete()
                .uri("/bookings/" + bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.tokenB())
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class)).isEqualTo(1);
    }

    /**
     * Day gio he thong ve qua khu thay vi cho 10 phut. Test khong duoc cho doi.
     */
    private void lamChoHetHan() {
        db.update("UPDATE seat_hold SET expires_at = now() - interval '1 minute' WHERE status = 'HELD'");
        db.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE status = 'PENDING'");
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
