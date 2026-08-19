package com.cinebook.payment.infra;

import com.cinebook.booking.api.BookingView;
import com.cinebook.booking.api.HoldExpiredException;
import com.cinebook.booking.api.BookingLookup;
import com.cinebook.identity.api.AccessControl;
import com.cinebook.payment.domain.PaymentGateway;
import com.cinebook.payment.domain.PaymentNotAllowedException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class InitiatePaymentUseCase {

    private static final String SQL_FIND_EXISTING = """
            SELECT id::text, amount FROM payments
             WHERE booking_id = CAST(:bookingId AS uuid) AND status = 'INITIATED'
            """;

    private static final String SQL_INSERT = """
            INSERT INTO payments
                (id, booking_id, provider, provider_txn_id, amount, status,
                 idempotency_key, created_at, updated_at)
            VALUES
                (CAST(:id AS uuid), CAST(:bookingId AS uuid), :provider, :providerTxnId,
                 :amount, 'INITIATED', :idempotencyKey, :now, :now)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final BookingLookup bookingQuery;
    private final AccessControl accessControl;
    private final PaymentGateway gateway;

    public InitiatePaymentUseCase(NamedParameterJdbcTemplate jdbc, BookingLookup bookingQuery,
                                  AccessControl accessControl, PaymentGateway gateway) {
        this.jdbc = jdbc;
        this.bookingQuery = bookingQuery;
        this.accessControl = accessControl;
        this.gateway = gateway;
    }

    @Transactional
    public Result initiate(UUID bookingId) {
        UUID owner = bookingQuery.findOwner(bookingId)
                .orElseThrow(() -> new PaymentNotAllowedException("Khong tim thay don dat ve"));
        // Kiem tra quyen TRUOC khi lam bat cu gi khac.
        accessControl.requireSelfOrAdmin(owner);

        BookingView booking = bookingQuery.findById(bookingId)
                .orElseThrow(() -> new PaymentNotAllowedException("Khong tim thay don dat ve"));

        if (!"PENDING".equals(booking.status())) {
            throw new PaymentNotAllowedException(
                    "Don o trang thai " + booking.status() + " nen khong thanh toan duoc");
        }
        if (booking.holdExpiresAt() == null || !booking.holdExpiresAt().isAfter(Instant.now())) {
            // 410 GONE, khong phai 422: hold DA TUNG hop le va gio khong con.
            throw new HoldExpiredException();
        }

        // Goi lai lan hai cho cung mot don thi tra ve giao dich dang co, khong tao them.
        List<Existing> daCo = jdbc.query(SQL_FIND_EXISTING,
                new MapSqlParameterSource().addValue("bookingId", bookingId.toString()),
                (rs, n) -> new Existing(UUID.fromString(rs.getString(1)), rs.getLong(2)));
        if (!daCo.isEmpty()) {
            Existing e = daCo.getFirst();
            return new Result(e.paymentId(),
                    gateway.createSession(e.paymentId(), e.amount(), booking.code()).redirectUrl(),
                    e.amount());
        }

        UUID paymentId = UUID.randomUUID();
        PaymentGateway.Session session =
                gateway.createSession(paymentId, booking.totalAmount(), booking.code());
        Instant now = Instant.now();

        jdbc.update(SQL_INSERT, new MapSqlParameterSource()
                .addValue("id", paymentId.toString())
                .addValue("bookingId", bookingId.toString())
                .addValue("provider", gateway.providerName())
                .addValue("providerTxnId", session.providerTxnId())
                .addValue("amount", booking.totalAmount())
                // Khoa chong charge trung: mot don chi co mot giao dich.
                .addValue("idempotencyKey", "booking:" + bookingId)
                .addValue("now", Timestamp.from(now)));

        return new Result(paymentId, session.redirectUrl(), booking.totalAmount());
    }

    public record Result(UUID paymentId, String redirectUrl, long amount) {
    }

    private record Existing(UUID paymentId, long amount) {
    }
}
