package com.cinebook.payment;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InitiatePaymentTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    private PaymentFixture fixture;

    @BeforeEach
    void reset() {
        fixture = PaymentFixture.freshSetup(this, db);
    }

    @Test
    void tao_giao_dich_tra_ve_redirect_url_va_ghi_dung_so_tien() {
        String bookingId = fixture.holdSeats(fixture.tokenA(), "A1", "A2");

        var response = initiate(fixture.tokenA(), bookingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("redirectUrl").asText()).startsWith("https://");
        assertThat(response.getBody().get("amount").asLong()).isEqualTo(180000L);

        Long soTien = db.queryForObject(
                "SELECT amount FROM payments WHERE booking_id = ?::uuid", Long.class, bookingId);
        assertThat(soTien).isEqualTo(180000L);

        String trangThai = db.queryForObject(
                "SELECT status FROM payments WHERE booking_id = ?::uuid", String.class, bookingId);
        assertThat(trangThai).isEqualTo("INITIATED");
    }

    @Test
    void goi_hai_lan_cho_cung_mot_don_khong_tao_hai_giao_dich() {
        String bookingId = fixture.holdSeats(fixture.tokenA(), "A1");

        initiate(fixture.tokenA(), bookingId);
        var lanHai = initiate(fixture.tokenA(), bookingId);

        assertThat(lanHai.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Integer soGiaoDich = db.queryForObject(
                "SELECT count(*) FROM payments WHERE booking_id = ?::uuid",
                Integer.class, bookingId);
        assertThat(soGiaoDich).isEqualTo(1);
    }

    @Test
    void khong_tao_duoc_giao_dich_cho_don_cua_nguoi_khac() {
        String bookingId = fixture.holdSeats(fixture.tokenA(), "A1");

        var response = initiate(fixture.tokenB(), bookingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(db.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
    }

    @Test
    void don_da_huy_thi_khong_thanh_toan_duoc() {
        String bookingId = fixture.holdSeats(fixture.tokenA(), "A1");
        client().delete()
                .uri("/bookings/" + bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.tokenA())
                .retrieve()
                .toBodilessEntity();

        var response = initiate(fixture.tokenA(), bookingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void hold_da_het_han_thi_khong_thanh_toan_duoc_nua() {
        String bookingId = fixture.holdSeats(fixture.tokenA(), "A1");
        fixture.lamChoHetHan(db);

        var response = initiate(fixture.tokenA(), bookingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    private ResponseEntity<JsonNode> initiate(String token, String bookingId) {
        return client().post()
                .uri("/bookings/" + bookingId + "/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
