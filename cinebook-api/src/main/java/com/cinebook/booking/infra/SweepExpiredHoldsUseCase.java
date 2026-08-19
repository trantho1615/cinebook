package com.cinebook.booking.infra;

import com.cinebook.shared.audit.AuditLogger;
import com.cinebook.shared.outbox.OutboxWriter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Lop thu hai cua co che het han (spec muc 6.6).
 *
 * KHONG chiu trach nhiem ve tinh dung dan — lazy expiration ngay trong HoldSeatsUseCase
 * moi la thu dam bao khong ai bi khoa ghe oan. Sweeper mua tinh KIP THOI: UI nguoi khac
 * thay ghe trong ra ma khong can ai thu giu. Test
 * khong_co_sweeper_thi_van_giu_duoc_ghe_da_het_han_cua_nguoi_khac giu lai su phan vai do.
 *
 * Chay o cinebook-worker qua SweeperJob, moi 30 giay, duoi khoa ShedLock: hai instance
 * cung quet se cung phat event BookingExpired cho cung mot don.
 */
@Component
public class SweepExpiredHoldsUseCase {

    private static final String SQL_RELEASE_SEATS = """
            UPDATE seat_hold
               SET status = 'EXPIRED', released_at = now(), release_reason = 'SWEPT'
             WHERE status = 'HELD'
               AND expires_at <= now()
            RETURNING seat_id::text
            """;

    private static final String SQL_EXPIRE_BOOKINGS = """
            UPDATE bookings
               SET status = 'EXPIRED', version = version + 1
             WHERE status = 'PENDING'
               AND hold_expires_at <= now()
            RETURNING id::text
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxWriter outbox;
    private final AuditLogger auditLogger;

    public SweepExpiredHoldsUseCase(NamedParameterJdbcTemplate jdbc, OutboxWriter outbox,
                                    AuditLogger auditLogger) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public int sweep() {
        // Nha ghe truoc, dong don sau. Nguoc lai thi trong khoanh khac giua hai cau se ton
        // tai don EXPIRED ma ghe van HELD — mot trang thai khong hop le. Cung transaction
        // nen khong ai thay that, nhung thu tu dung van re hon viec phai di giai thich.
        List<String> gheDaNha = jdbc.query(SQL_RELEASE_SEATS,
                Collections.emptyMap(), (rs, n) -> rs.getString(1));

        List<String> donHetHan = jdbc.query(SQL_EXPIRE_BOOKINGS,
                Collections.emptyMap(), (rs, n) -> rs.getString(1));

        for (String bookingId : donHetHan) {
            UUID id = UUID.fromString(bookingId);
            // Cung transaction voi hai cau UPDATE tren — khong co khe ho "da het han nhung
            // khong ai duoc bao".
            outbox.write("BOOKING", id, "BookingExpired",
                    "{\"bookingId\":\"" + bookingId + "\"}");
            auditLogger.record("BOOKING", id, "EXPIRED",
                    AuditLogger.ActorType.SYSTEM, null, "{\"by\":\"SWEEPER\"}");
        }

        return gheDaNha.size();
    }
}
