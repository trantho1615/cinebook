package com.cinebook.booking;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MyBookingsTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void chi_thay_don_cua_chinh_minh() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        hold(fixture.tokenB(), fixture.seatIds("A2"));

        var body = client().get()
                .uri("/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.tokenA())
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("seats").get(0).asText()).isEqualTo("A1");
    }

    @Test
    void don_tra_ve_kem_thong_tin_phim_va_rap() {
        hold(fixture.tokenA(), fixture.seatIds("A1", "A2"));

        var body = client().get()
                .uri("/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.tokenA())
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();

        JsonNode don = body.get(0);
        assertThat(don.get("movieTitle").asText()).isEqualTo("Phim Test");
        assertThat(don.get("cinemaName").asText()).isEqualTo("CGV Quan 1");
        assertThat(don.get("seats").size()).isEqualTo(2);
        assertThat(don.get("totalAmount").asLong()).isEqualTo(180000L);
    }

    @Test
    void khong_xem_duoc_don_cua_nguoi_khac() {
        String bookingId = hold(fixture.tokenA(), fixture.seatIds("A1"))
                .get("bookingId").asText();

        var response = client().get()
                .uri("/bookings/" + bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.tokenB())
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).doesNotContain("A1");
    }

    private JsonNode hold(String token, List<String> seatIds) {
        return client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", seatIds))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();
    }
}
