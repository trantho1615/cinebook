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

class IdempotencyTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
        db.execute("TRUNCATE TABLE idempotency_keys");
    }

    @Test
    void gui_lai_cung_mot_key_tra_ve_dung_ket_qua_cu_va_khong_tao_booking_thu_hai() {
        var lanDau = hold("key-abc-123", fixture.seatIds("A1", "A2"));
        assertThat(lanDau.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String maLanDau = lanDau.getBody().get("code").asText();

        var lanHai = hold("key-abc-123", fixture.seatIds("A1", "A2"));

        assertThat(lanHai.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(lanHai.getBody().get("code").asText()).isEqualTo(maLanDau);
        assertThat(db.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class)).isEqualTo(2);
    }

    @Test
    void key_khac_nhau_thi_van_la_hai_yeu_cau_doc_lap() {
        hold("key-1", fixture.seatIds("A1"));
        var lanHai = hold("key-2", fixture.seatIds("A2"));

        assertThat(lanHai.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(db.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(2);
    }

    @Test
    void khong_gui_key_thi_request_van_chay_binh_thuong() {
        var response = client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.tokenA())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", fixture.seatIds("A1")))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void yeu_cau_that_bai_khong_chiem_giu_key_nen_thu_lai_duoc() {
        // Nguoi A giu A1 truoc
        hold("key-cua-A", fixture.seatIds("A1"));

        // Nguoi B dung key rieng, xin dung ghe do -> that bai
        var thatBai = holdAs(fixture.tokenB(), "key-cua-B", fixture.seatIds("A1"));
        assertThat(thatBai.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Cung key do, nhung lan nay xin ghe con trong -> phai thanh cong,
        // key khong duoc "khoa" boi lan that bai truoc.
        var thanhCong = holdAs(fixture.tokenB(), "key-cua-B", fixture.seatIds("A5"));
        assertThat(thanhCong.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<JsonNode> hold(String key, List<String> seatIds) {
        return holdAs(fixture.tokenA(), key, seatIds);
    }

    private ResponseEntity<JsonNode> holdAs(String token, String key, List<String> seatIds) {
        return client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", seatIds))
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
