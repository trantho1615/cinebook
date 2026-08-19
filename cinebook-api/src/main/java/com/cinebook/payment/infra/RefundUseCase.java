package com.cinebook.payment.infra;

import com.cinebook.payment.domain.PaymentGateway;
import com.cinebook.shared.audit.AuditLogger;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class RefundUseCase {

    private static final String SQL_FIND = """
            SELECT amount, provider_txn_id FROM payments WHERE id = CAST(:paymentId AS uuid)
            """;

    private static final String SQL_INSERT_REFUND = """
            INSERT INTO refunds (id, payment_id, amount, reason, status, provider_refund_id, created_at)
            VALUES (CAST(:id AS uuid), CAST(:paymentId AS uuid), :amount, :reason,
                    'SUCCEEDED', :providerRefundId, :createdAt)
            """;

    private static final String SQL_MARK_REFUNDED = """
            UPDATE payments SET status = 'REFUNDED', updated_at = now()
             WHERE id = CAST(:paymentId AS uuid)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentGateway gateway;
    private final AuditLogger auditLogger;

    public RefundUseCase(NamedParameterJdbcTemplate jdbc, PaymentGateway gateway,
                         AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.auditLogger = auditLogger;
    }

    /**
     * Webhook den sau khi hold het han va ghe da co chu moi. Khong xac nhan don duoc
     * nen phai tra lai tien — day la tinh huong ma hau het project bo qua.
     */
    @Transactional
    public void refundBecauseSeatsLost(UUID paymentId) {
        List<Row> rows = jdbc.query(SQL_FIND,
                new MapSqlParameterSource().addValue("paymentId", paymentId.toString()),
                (rs, n) -> new Row(rs.getLong("amount"), rs.getString("provider_txn_id")));
        if (rows.isEmpty()) {
            return;
        }
        Row payment = rows.getFirst();

        String providerRefundId = gateway.refund(payment.providerTxnId(), payment.amount());

        jdbc.update(SQL_INSERT_REFUND, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("paymentId", paymentId.toString())
                .addValue("amount", payment.amount())
                .addValue("reason", "SEATS_LOST_BEFORE_CONFIRMATION")
                .addValue("providerRefundId", providerRefundId)
                .addValue("createdAt", Timestamp.from(Instant.now())));

        jdbc.update(SQL_MARK_REFUNDED,
                new MapSqlParameterSource().addValue("paymentId", paymentId.toString()));

        auditLogger.record("PAYMENT", paymentId, "REFUNDED",
                AuditLogger.ActorType.SYSTEM, null,
                "{\"reason\":\"SEATS_LOST_BEFORE_CONFIRMATION\"}");
    }

    private record Row(long amount, String providerTxnId) {
    }
}
