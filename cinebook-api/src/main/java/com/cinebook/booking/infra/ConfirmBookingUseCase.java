package com.cinebook.booking.infra;

import com.cinebook.booking.api.BookingConfirmation;
import com.cinebook.booking.api.HoldExpiredException;
import com.cinebook.booking.domain.TicketCode;
import com.cinebook.shared.audit.AuditLogger;
import com.cinebook.shared.outbox.OutboxWriter;
import com.cinebook.shared.realtime.SeatMapChannel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ConfirmBookingUseCase implements BookingConfirmation {

    /**
     * Dieu kien status = 'HELD' la thu phat hien webhook den muon: neu hold da het han
     * va ghe da co chu moi, cau nay anh huong 0 dong. Khong can mot cau SELECT
     * kiem tra rieng nao, va khong co khoang trong giua kiem tra va ghi.
     */
    private static final String SQL_MARK_BOOKED = """
            UPDATE seat_hold
               SET status = 'BOOKED', expires_at = NULL
             WHERE booking_id = CAST(:bookingId AS uuid)
               AND status = 'HELD'
            """;

    private static final String SQL_CONFIRM_BOOKING = """
            UPDATE bookings
               SET status = 'CONFIRMED', confirmed_at = now(), version = version + 1
             WHERE id = CAST(:bookingId AS uuid)
               AND status = 'PENDING'
            """;

    private static final String SQL_LIST_ITEMS = """
            SELECT id::text FROM booking_items WHERE booking_id = CAST(:bookingId AS uuid)
            """;

    private static final String SQL_SET_TICKET_CODE = """
            UPDATE booking_items SET ticket_code = :ticketCode
             WHERE id = CAST(:itemId AS uuid) AND ticket_code IS NULL
            """;

    private static final String SQL_RELEASE_HOLDS = """
            UPDATE seat_hold
               SET status = 'EXPIRED', released_at = now(), release_reason = 'PAYMENT_FAILED'
             WHERE booking_id = CAST(:bookingId AS uuid)
               AND status = 'HELD'
            """;

    private static final String SQL_CANCEL_BOOKING = """
            UPDATE bookings
               SET status = 'CANCELLED', cancelled_at = now(), version = version + 1
             WHERE id = CAST(:bookingId AS uuid)
               AND status = 'PENDING'
            """;

    private static final String SQL_SEAT_MAP_INFO = """
            SELECT b.showtime_id::text AS showtime_id, i.seat_label
              FROM bookings b
              JOIN booking_items i ON i.booking_id = b.id
             WHERE b.id = CAST(:bookingId AS uuid)
             ORDER BY i.seat_label
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogger auditLogger;
    private final OutboxWriter outbox;
    private final SeatMapChannel seatMapChannel;

    public ConfirmBookingUseCase(NamedParameterJdbcTemplate jdbc, AuditLogger auditLogger,
                                 OutboxWriter outbox, SeatMapChannel seatMapChannel) {
        this.jdbc = jdbc;
        this.auditLogger = auditLogger;
        this.outbox = outbox;
        this.seatMapChannel = seatMapChannel;
    }

    /**
     * REQUIRES_NEW: method nay nem HoldExpiredException de bao hieu webhook den muon.
     * Neu dung chung transaction voi phia goi thi exception se danh dau transaction do
     * la rollback-only, va lenh hoan tien ngay sau se chet luc commit voi thong bao
     * "Transaction silently rolled back because it has been marked as rollback-only".
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(UUID bookingId, UUID paymentId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("bookingId", bookingId.toString());

        int soGheDoiDuoc = jdbc.update(SQL_MARK_BOOKED, params);
        if (soGheDoiDuoc == 0) {
            // Da co ai do lay ghe: webhook den sau khi hold het han.
            throw new HoldExpiredException();
        }

        int soDonDoiDuoc = jdbc.update(SQL_CONFIRM_BOOKING, params);
        if (soDonDoiDuoc == 0) {
            // Don khong con o PENDING — webhook gui lai sau khi da xac nhan.
            // Khong phai loi, chi la khong co gi de lam them.
            return;
        }

        for (String itemId : jdbc.query(SQL_LIST_ITEMS, params, (rs, n) -> rs.getString(1))) {
            jdbc.update(SQL_SET_TICKET_CODE, new MapSqlParameterSource()
                    .addValue("itemId", itemId)
                    .addValue("ticketCode", TicketCode.generate()));
        }

        // Ghi event trong CHINH transaction vua doi trang thai don: hoac ca hai cung
        // ton tai, hoac ca hai cung khong. Khong con khe ho "da xac nhan nhung event bay mat".
        outbox.write("BOOKING", bookingId, "BookingConfirmed",
                "{\"bookingId\":\"" + bookingId + "\",\"paymentId\":\"" + paymentId + "\"}");

        auditLogger.record("BOOKING", bookingId, "CONFIRMED",
                AuditLogger.ActorType.SYSTEM, null,
                "{\"paymentId\":\"" + paymentId + "\"}");

        baoSoDoGheDoi(bookingId, "BOOKED");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseAfterFailedPayment(UUID bookingId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("bookingId", bookingId.toString());
        jdbc.update(SQL_RELEASE_HOLDS, params);
        jdbc.update(SQL_CANCEL_BOOKING, params);

        outbox.write("BOOKING", bookingId, "BookingPaymentFailed",
                "{\"bookingId\":\"" + bookingId + "\"}");

        auditLogger.record("BOOKING", bookingId, "PAYMENT_FAILED",
                AuditLogger.ActorType.SYSTEM, null, "{}");

        baoSoDoGheDoi(bookingId, "RELEASED");
    }

    private void baoSoDoGheDoi(UUID bookingId, String status) {
        var rows = jdbc.query(SQL_SEAT_MAP_INFO,
                new MapSqlParameterSource().addValue("bookingId", bookingId.toString()),
                (rs, n) -> new String[]{rs.getString("showtime_id"), rs.getString("seat_label")});
        if (rows.isEmpty()) {
            return;
        }
        seatMapChannel.seatsChanged(UUID.fromString(rows.getFirst()[0]), status,
                rows.stream().map(row -> row[1]).toList());
    }
}
