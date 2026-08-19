package com.cinebook.payment.infra;

import com.cinebook.booking.api.BookingConfirmation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Component
public class ProcessPaymentUseCase {

    private static final String SQL_FIND_PAYMENT = """
            SELECT booking_id::text, status FROM payments WHERE id = CAST(:paymentId AS uuid)
            """;

    private static final String SQL_UPDATE_STATUS = """
            UPDATE payments
               SET status = :status,
                   provider_txn_id = COALESCE(:providerTxnId, provider_txn_id),
                   updated_at = now()
             WHERE id = CAST(:paymentId AS uuid) AND status = 'INITIATED'
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final BookingConfirmation bookingConfirmation;

    public ProcessPaymentUseCase(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                                 BookingConfirmation bookingConfirmation) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.bookingConfirmation = bookingConfirmation;
    }

    @Transactional
    public void process(String rawBody) {
        JsonNode json = objectMapper.readTree(rawBody);
        UUID paymentId = UUID.fromString(json.path("paymentId").asText());
        String status = json.path("status").asText();
        String providerTxnId = json.path("providerTxnId").asText(null);

        List<PaymentRow> rows = jdbc.query(SQL_FIND_PAYMENT,
                new MapSqlParameterSource().addValue("paymentId", paymentId.toString()),
                (rs, n) -> new PaymentRow(UUID.fromString(rs.getString(1)), rs.getString(2)));
        if (rows.isEmpty() || !"INITIATED".equals(rows.getFirst().status())) {
            // Giao dich khong ton tai hoac da xu ly roi.
            return;
        }
        UUID bookingId = rows.getFirst().bookingId();

        if ("SUCCEEDED".equals(status)) {
            jdbc.update(SQL_UPDATE_STATUS, new MapSqlParameterSource()
                    .addValue("paymentId", paymentId.toString())
                    .addValue("status", "SUCCEEDED")
                    .addValue("providerTxnId", providerTxnId));
            bookingConfirmation.confirm(bookingId, paymentId);
        } else {
            jdbc.update(SQL_UPDATE_STATUS, new MapSqlParameterSource()
                    .addValue("paymentId", paymentId.toString())
                    .addValue("status", "FAILED")
                    .addValue("providerTxnId", providerTxnId));
            bookingConfirmation.releaseAfterFailedPayment(bookingId);
        }
    }

    private record PaymentRow(UUID bookingId, String status) {
    }
}
