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

class ConfirmBookingTest extends AbstractApiTest {

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
        bookingId = fixture.holdSeats(fixture.tokenA(), "A1", "A2");
        fixture.initiatePayment(this, fixture.tokenA(), bookingId);
        paymentId = db.queryForObject(
                "SELECT id::text FROM payments WHERE booking_id = ?::uuid", String.class, bookingId);
    }

    @Test
    void thanh_toan_thanh_cong_doi_ca_bon_thu_trong_mot_transaction() {
        guiWebhookThanhCong("evt-1");

        assertThat(trangThaiPayment()).isEqualTo("SUCCEEDED");
        assertThat(trangThaiBooking()).isEqualTo("CONFIRMED");

        Integer soGheBooked = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE booking_id = ?::uuid AND status = 'BOOKED'",
                Integer.class, bookingId);
        assertThat(soGheBooked).isEqualTo(2);

        Integer soVeCoMa = db.queryForObject("""
                SELECT count(*) FROM booking_items
                 WHERE booking_id = ?::uuid AND ticket_code IS NOT NULL
                """, Integer.class, bookingId);
        assertThat(soVeCoMa).isEqualTo(2);
    }

    @Test
    void ghe_da_BOOKED_thi_khong_con_expires_at() {
        guiWebhookThanhCong("evt-1");

        Integer soConHan = db.queryForObject("""
                SELECT count(*) FROM seat_hold
                 WHERE booking_id = ?::uuid AND status = 'BOOKED' AND expires_at IS NOT NULL
                """, Integer.class, bookingId);
        assertThat(soConHan).isZero();
    }

    @Test
    void moi_ve_co_ma_rieng_khong_trung_nhau() {
        guiWebhookThanhCong("evt-1");

        Integer soMaKhacNhau = db.queryForObject("""
                SELECT count(DISTINCT ticket_code) FROM booking_items WHERE booking_id = ?::uuid
                """, Integer.class, bookingId);
        assertThat(soMaKhacNhau).isEqualTo(2);
    }

    @Test
    void gui_lai_webhook_thanh_cong_khong_lam_doi_gi_them() {
        guiWebhookThanhCong("evt-1");
        String maVeBanDau = db.queryForObject("""
                SELECT ticket_code FROM booking_items WHERE booking_id = ?::uuid
                 ORDER BY seat_label LIMIT 1
                """, String.class, bookingId);

        guiWebhookThanhCong("evt-1");

        assertThat(trangThaiBooking()).isEqualTo("CONFIRMED");
        String maVeSau = db.queryForObject("""
                SELECT ticket_code FROM booking_items WHERE booking_id = ?::uuid
                 ORDER BY seat_label LIMIT 1
                """, String.class, bookingId);
        assertThat(maVeSau).isEqualTo(maVeBanDau);
    }

    @Test
    void thanh_toan_that_bai_thi_ghe_duoc_giai_phong() {
        String body = "{\"eventId\":\"evt-fail\",\"paymentId\":\"" + paymentId
                + "\",\"status\":\"FAILED\"}";
        client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", signature.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        assertThat(trangThaiPayment()).isEqualTo("FAILED");
        assertThat(trangThaiBooking()).isEqualTo("CANCELLED");

        Integer soConGiu = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE booking_id = ?::uuid AND status = 'HELD'",
                Integer.class, bookingId);
        assertThat(soConGiu).isZero();

        Integer soPaymentFailed = db.queryForObject("""
                SELECT count(*) FROM seat_hold
                 WHERE booking_id = ?::uuid AND release_reason = 'PAYMENT_FAILED'
                """, Integer.class, bookingId);
        assertThat(soPaymentFailed).isEqualTo(2);
    }

    private void guiWebhookThanhCong(String eventId) {
        String body = "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + paymentId
                + "\",\"status\":\"SUCCEEDED\",\"providerTxnId\":\"MOCK-1\"}";
        var response = client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", signature.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
