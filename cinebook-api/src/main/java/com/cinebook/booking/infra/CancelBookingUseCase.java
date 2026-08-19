package com.cinebook.booking.infra;

import com.cinebook.booking.domain.BookingNotFoundException;
import com.cinebook.identity.api.AccessControl;
import com.cinebook.shared.audit.AuditLogger;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class CancelBookingUseCase {

    private static final String SQL_FIND_OWNER = """
            SELECT user_id FROM bookings WHERE id = CAST(:bookingId AS uuid)
            """;

    private static final String SQL_RELEASE_HOLDS = """
            UPDATE seat_hold
               SET status = 'EXPIRED', released_at = now(), release_reason = 'USER_CANCELLED'
             WHERE booking_id = CAST(:bookingId AS uuid)
               AND status = 'HELD'
            """;

    private static final String SQL_CANCEL_BOOKING = """
            UPDATE bookings
               SET status = 'CANCELLED', cancelled_at = now(), version = version + 1
             WHERE id = CAST(:bookingId AS uuid)
               AND status = 'PENDING'
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessControl accessControl;
    private final AuditLogger auditLogger;

    public CancelBookingUseCase(NamedParameterJdbcTemplate jdbc, AccessControl accessControl,
                                AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.accessControl = accessControl;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public void cancel(UUID bookingId) {
        List<UUID> owners = jdbc.query(SQL_FIND_OWNER,
                new MapSqlParameterSource().addValue("bookingId", bookingId.toString()),
                (rs, rowNum) -> UUID.fromString(rs.getString("user_id")));

        if (owners.isEmpty()) {
            throw new BookingNotFoundException(bookingId);
        }
        // Kiem tra quyen TRUOC khi thay doi bat cu thu gi.
        accessControl.requireSelfOrAdmin(owners.getFirst());

        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("bookingId", bookingId.toString());
        jdbc.update(SQL_RELEASE_HOLDS, params);
        jdbc.update(SQL_CANCEL_BOOKING, params);

        auditLogger.record("BOOKING", bookingId, "CANCELLED",
                AuditLogger.ActorType.USER, owners.getFirst(), "{}");
    }
}
