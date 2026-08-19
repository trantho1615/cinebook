package com.cinebook.payment;

import com.cinebook.payment.infra.WebhookSignature;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookIdempotencyTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    WebhookSignature signature;

    private PaymentFixture fixture;
    private String paymentId;

    @BeforeEach
    void reset() {
        fixture = PaymentFixture.freshSetup(this, db);
        String bookingId = fixture.holdSeats(fixture.tokenA(), "A1", "A2");
        fixture.initiatePayment(this, fixture.tokenA(), bookingId);
        paymentId = db.queryForObject(
                "SELECT id::text FROM payments WHERE booking_id = ?::uuid", String.class, bookingId);
    }

    @Test
    void gui_cung_mot_webhook_nam_lan_chi_ghi_nhan_mot_lan() {
        String body = webhookBody("evt-trung-lap");

        for (int i = 0; i < 5; i++) {
            var response = guiWebhook(body);
            // Provider luon phai nhan 200, ke ca lan trung — neu khong no se retry mai.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        Integer soEvent = db.queryForObject(
                "SELECT count(*) FROM payment_events WHERE provider_event_id = 'evt-trung-lap'",
                Integer.class);
        assertThat(soEvent).isEqualTo(1);
    }

    @Test
    void event_id_khac_nhau_thi_deu_duoc_ghi_nhan() {
        guiWebhook(webhookBody("evt-1"));
        guiWebhook(webhookBody("evt-2"));

        Integer soEvent = db.queryForObject("SELECT count(*) FROM payment_events", Integer.class);
        assertThat(soEvent).isEqualTo(2);
    }

    @Test
    void chu_ky_sai_bi_tu_choi_401_nhung_van_duoc_luu_lai_de_dieu_tra() {
        var response = client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", "chu-ky-gia-mao")
                .contentType(MediaType.APPLICATION_JSON)
                .body(webhookBody("evt-gia-mao"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Van luu lai payload tho: day la thu cuu ban khi phai dieu tra tranh chap.
        Integer soEvent = db.queryForObject("""
                SELECT count(*) FROM payment_events
                 WHERE provider_event_id = 'evt-gia-mao' AND signature_valid = false
                """, Integer.class);
        assertThat(soEvent).isEqualTo(1);
    }

    @Test
    void webhook_thieu_hoan_toan_header_chu_ky_bi_tu_choi() {
        var response = client().post()
                .uri("/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .body(webhookBody("evt-khong-chu-ky"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void payload_tho_duoc_luu_nguyen_van() {
        String body = webhookBody("evt-luu-tho");
        guiWebhook(body);

        String daLuu = db.queryForObject("""
                SELECT raw_payload::text FROM payment_events WHERE provider_event_id = 'evt-luu-tho'
                """, String.class);
        assertThat(daLuu).contains("evt-luu-tho").contains(paymentId);
    }

    private String webhookBody(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + paymentId
                + "\",\"status\":\"SUCCEEDED\",\"providerTxnId\":\"MOCK-1\"}";
    }

    private ResponseEntity<String> guiWebhook(String body) {
        return client().post()
                .uri("/webhooks/payment")
                .header("X-Signature", signature.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }
}
