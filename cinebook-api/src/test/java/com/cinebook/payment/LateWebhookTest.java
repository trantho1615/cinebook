package com.cinebook.payment;

import com.cinebook.payment.infra.WebhookSignature;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class LateWebhookTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    WebhookSignature signature;

    private PaymentFixture fixture;
    private String bookingId;
    private String paymentId;

    @BeforeEach
    void reset() {
        fixture = PaymentFixture.freshSetup(this, db);
        bookingId = fixture.holdSeats(fixture.tokenA(), "A1");
        fixture.initiatePayment(this, fixture.tokenA(), bookingId);
        paymentId = db.queryForObject(
                "SELECT id::text FROM payments WHERE booking_id = ?::uuid", String.class, bookingId);
    }

    @Test
    void webhook_den_sau_khi_ghe_da_bi_nguoi_khac_lay_thi_tu_dong_hoan_tien() {
        fixture.lamChoHetHan(db);
        fixture.holdSeats(fixture.tokenB(), "A1");

        guiWebhookThanhCong("evt-muon");

        assertThat(trangThaiBooking()).isNotEqualTo("CONFIRMED");

        Integer soHoanTien = db.queryForObject(
                "SELECT count(*) FROM refunds WHERE payment_id = ?::uuid", Integer.class, paymentId);
        assertThat(soHoanTien).isEqualTo(1);

        assertThat(trangThaiPayment()).isEqualTo("REFUNDED");
    }

    @Test
    void webhook_den_muon_van_tra_200_cho_cong_thanh_toan() {
        fixture.lamChoHetHan(db);
        fixture.holdSeats(fixture.tokenB(), "A1");

        String body = webhookBody("evt-muon");
        var response = client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", signature.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        // Tra 500 thi cong thanh toan se retry mai ma khong bao gio thanh cong.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ghe_cua_nguoi_moi_khong_bi_dong_toi() {
        fixture.lamChoHetHan(db);
        String bookingCuaB = fixture.holdSeats(fixture.tokenB(), "A1");

        guiWebhookThanhCong("evt-muon");

        String trangThaiB = db.queryForObject(
                "SELECT status FROM bookings WHERE id = ?::uuid", String.class, bookingCuaB);
        assertThat(trangThaiB).isEqualTo("PENDING");

        Integer gheCuaBVanCon = db.queryForObject("""
                SELECT count(*) FROM seat_hold WHERE booking_id = ?::uuid AND status = 'HELD'
                """, Integer.class, bookingCuaB);
        assertThat(gheCuaBVanCon).isEqualTo(1);
    }

    @Test
    void hoan_tien_duoc_ghi_audit_voi_actor_la_SYSTEM() {
        fixture.lamChoHetHan(db);
        fixture.holdSeats(fixture.tokenB(), "A1");

        guiWebhookThanhCong("evt-muon");

        Integer soAudit = db.queryForObject("""
                SELECT count(*) FROM audit_log
                 WHERE action = 'REFUNDED' AND actor_type = 'SYSTEM'
                """, Integer.class);
        assertThat(soAudit).isEqualTo(1);
    }

    private String webhookBody(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + paymentId
                + "\",\"status\":\"SUCCEEDED\",\"providerTxnId\":\"MOCK-1\"}";
    }

    private void guiWebhookThanhCong(String eventId) {
        String body = webhookBody(eventId);
        client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", signature.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String trangThaiPayment() {
        return db.queryForObject("SELECT status FROM payments WHERE id = ?::uuid",
                String.class, paymentId);
    }

    private String trangThaiBooking() {
        return db.queryForObject("SELECT status FROM bookings WHERE id = ?::uuid",
                String.class, bookingId);
    }
}
