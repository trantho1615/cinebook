package com.cinebook.payment;

import com.cinebook.payment.infra.WebhookSignature;
import com.cinebook.shared.outbox.OutboxRelay;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    WebhookSignature signature;

    @Autowired
    OutboxRelay relay;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    com.cinebook.shared.outbox.OutboxWriter outbox;

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
    void xac_nhan_don_thi_event_duoc_ghi_vao_outbox_cung_transaction() {
        guiWebhookThanhCong("evt-1");

        Integer soEvent = db.queryForObject("""
                SELECT count(*) FROM outbox_events
                 WHERE event_type = 'BookingConfirmed' AND aggregate_id = ?::uuid
                """, Integer.class, bookingId);
        assertThat(soEvent).isEqualTo(1);

        // Chua relay thi chua publish
        Integer chuaGui = db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Integer.class);
        assertThat(chuaGui).isEqualTo(1);
    }

    @Test
    void relay_danh_dau_da_gui_va_khong_gui_lai_lan_hai() {
        guiWebhookThanhCong("evt-1");

        int lanDau = relay.relayBatch(100);
        assertThat(lanDau).isEqualTo(1);

        int lanHai = relay.relayBatch(100);
        assertThat(lanHai).isZero();

        Integer conTreo = db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Integer.class);
        assertThat(conTreo).isZero();
    }

    @Test
    void event_khong_mat_khi_tien_trinh_chet_truoc_luc_relay() {
        guiWebhookThanhCong("evt-1");

        // Gia lap tien trinh chet: khong goi relay, event van nam nguyen trong bang
        Integer conTreo = db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Integer.class);
        assertThat(conTreo).isEqualTo(1);

        // Tien trinh moi bat len va relay tiep — event van con nguyen
        assertThat(relay.relayBatch(100)).isEqualTo(1);
    }

    @Test
    void event_mang_du_thong_tin_de_ben_tieu_thu_lam_viec() {
        guiWebhookThanhCong("evt-1");

        String payload = db.queryForObject("""
                SELECT payload::text FROM outbox_events WHERE event_type = 'BookingConfirmed'
                """, String.class);

        assertThat(payload).contains(bookingId).contains(paymentId);
    }

    @Test
    void thanh_toan_that_bai_cung_sinh_event_rieng() {
        String body = "{\"eventId\":\"evt-fail\",\"paymentId\":\"" + paymentId
                + "\",\"status\":\"FAILED\"}";
        client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", signature.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        Integer soEvent = db.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE event_type = 'BookingPaymentFailed'
                """, Integer.class);
        assertThat(soEvent).isEqualTo(1);
    }

    /**
     * Bang chung cho loi hua cot loi cua outbox: event va thay doi nghiep vu cung song
     * cung chet. Neu OutboxWriter tu mo transaction rieng (vi du REQUIRES_NEW) thi test
     * nay do ngay — da kiem chung bang cach doi tam propagation.
     */
    @Test
    void event_bi_rollback_cung_voi_thay_doi_nghiep_vu() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            db.update("UPDATE bookings SET status = 'CANCELLED' WHERE id = ?::uuid", bookingId);
            outbox.write("BOOKING", java.util.UUID.fromString(bookingId), "BookingConfirmed", "{}");
            throw new IllegalStateException("su co giua chung");
        })).isInstanceOf(IllegalStateException.class);

        Integer soEvent = db.queryForObject(
                "SELECT count(*) FROM outbox_events", Integer.class);
        assertThat(soEvent).isZero();

        String trangThai = db.queryForObject(
                "SELECT status FROM bookings WHERE id = ?::uuid", String.class, bookingId);
        assertThat(trangThai).isEqualTo("PENDING");
    }

    private void guiWebhookThanhCong(String eventId) {
        String body = "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + paymentId
                + "\",\"status\":\"SUCCEEDED\",\"providerTxnId\":\"MOCK-1\"}";
        client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", signature.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
