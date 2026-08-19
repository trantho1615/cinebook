package com.cinebook.payment.infra;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class RecordWebhookUseCase {

    public enum Outcome {
        PROCESSED,
        DUPLICATE
    }

    private static final String SQL_INSERT_EVENT = """
            INSERT INTO payment_events
                (payment_id, provider, provider_event_id, raw_payload, signature_valid, received_at)
            VALUES
                (CAST(:paymentId AS uuid), :provider, :providerEventId,
                 CAST(:rawPayload AS jsonb), :signatureValid, :receivedAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RecordWebhookUseCase(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * REQUIRES_NEW: ban ghi webhook phai duoc luu lai KE CA khi phan xu ly phia sau
     * that bai va rollback. Khong co no thi mot webhook gay loi se bien mat khong
     * dau vet, dung luc ban can no nhat de dieu tra.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome record(String rawBody, boolean signatureValid) {
        JsonNode json = objectMapper.readTree(rawBody);
        String eventId = json.path("eventId").asText();
        String paymentId = json.path("paymentId").asText(null);

        try {
            jdbc.update(SQL_INSERT_EVENT, new MapSqlParameterSource()
                    .addValue("paymentId", paymentId)
                    .addValue("provider", "MOCK")
                    .addValue("providerEventId", eventId)
                    .addValue("rawPayload", rawBody)
                    .addValue("signatureValid", signatureValid)
                    .addValue("receivedAt", Timestamp.from(Instant.now())));
            return Outcome.PROCESSED;
        } catch (DuplicateKeyException e) {
            // Cong thanh toan gui lai — da xu ly roi, khong lam gi them.
            return Outcome.DUPLICATE;
        }
    }
}
