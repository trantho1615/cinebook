package com.cinebook.payment.infra;

import com.cinebook.payment.domain.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Xu ly tinh huong "webhook khong bao gio den".
 *
 * Khong the ngoi cho mai: quet cac giao dich con treo qua lau roi CHU DONG hoi cong
 * thanh toan xem that su da thanh cong chua. Milestone worker se goi dinh ky.
 */
@Component
public class ReconcilePaymentsUseCase {

    private static final String SQL_FIND_STALE = """
            SELECT id::text, provider_txn_id
              FROM payments
             WHERE status = 'INITIATED'
               AND created_at <= now() - CAST(:threshold AS interval)
             ORDER BY created_at
             LIMIT 100
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentGateway gateway;
    private final ProcessPaymentUseCase processPayment;
    private final Duration reconcileAfter;

    public ReconcilePaymentsUseCase(NamedParameterJdbcTemplate jdbc, PaymentGateway gateway,
                                    ProcessPaymentUseCase processPayment,
                                    @Value("${cinebook.payment.reconcile-after}")
                                    Duration reconcileAfter) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.processPayment = processPayment;
        this.reconcileAfter = reconcileAfter;
    }

    public int reconcile() {
        List<Stale> danhSach = jdbc.query(SQL_FIND_STALE,
                new MapSqlParameterSource()
                        .addValue("threshold", reconcileAfter.toSeconds() + " seconds"),
                (rs, n) -> new Stale(UUID.fromString(rs.getString(1)), rs.getString(2)));

        int daXuLy = 0;
        for (Stale item : danhSach) {
            PaymentGateway.RemoteStatus remote = gateway.queryStatus(item.providerTxnId());
            if (remote == PaymentGateway.RemoteStatus.PENDING) {
                continue;
            }
            // Di lai dung duong ma webhook se di, de khong co hai nhanh logic khac nhau
            // cho cung mot ket qua.
            String payload = "{\"eventId\":\"reconcile-" + item.paymentId()
                    + "\",\"paymentId\":\"" + item.paymentId()
                    + "\",\"status\":\"" + remote.name()
                    + "\",\"providerTxnId\":\"" + item.providerTxnId() + "\"}";
            processPayment.process(payload);
            daXuLy++;
        }
        return daXuLy;
    }

    private record Stale(UUID paymentId, String providerTxnId) {
    }
}
