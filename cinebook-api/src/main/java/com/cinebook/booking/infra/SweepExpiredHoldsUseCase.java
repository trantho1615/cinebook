package com.cinebook.booking.infra;

import com.cinebook.shared.audit.AuditLogger;
import com.cinebook.shared.metrics.BookingMetrics;
import com.cinebook.shared.outbox.OutboxWriter;
import com.cinebook.shared.realtime.SeatMapChannel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
            RETURNING showtime_id::text, seat_id::text
            """;

    private static final String SQL_EXPIRE_BOOKINGS = """
            UPDATE bookings
               SET status = 'EXPIRED', version = version + 1
             WHERE status = 'PENDING'
               AND hold_expires_at <= now()
            RETURNING id::text
            """;

    private static final String SQL_SEAT_LABELS = """
            SELECT row_label || seat_number AS label
              FROM seats
             WHERE id = ANY(CAST(:seatIds AS uuid[]))
             ORDER BY row_label, seat_number
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxWriter outbox;
    private final AuditLogger auditLogger;
    private final SeatMapChannel seatMapChannel;
    private final BookingMetrics metrics;

    public SweepExpiredHoldsUseCase(NamedParameterJdbcTemplate jdbc, OutboxWriter outbox,
                                    AuditLogger auditLogger, SeatMapChannel seatMapChannel,
                                    BookingMetrics metrics) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.auditLogger = auditLogger;
        this.seatMapChannel = seatMapChannel;
        this.metrics = metrics;
    }

    @Transactional
    public int sweep() {
        // Nha ghe truoc, dong don sau. Nguoc lai thi trong khoanh khac giua hai cau se ton
        // tai don EXPIRED ma ghe van HELD — mot trang thai khong hop le. Cung transaction
        // nen khong ai thay that, nhung thu tu dung van re hon viec phai di giai thich.
        List<GheDaNha> gheDaNha = jdbc.query(SQL_RELEASE_SEATS, Collections.emptyMap(),
                (rs, n) -> new GheDaNha(rs.getString(1), rs.getString(2)));

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

        baoSoDoGheDoi(gheDaNha);
        metrics.ghiNhanSweeperNha(gheDaNha.size());

        return gheDaNha.size();
    }

    /**
     * Mot ban tin cho moi suat chieu, khong phai mot ban tin cho moi ghe: sweeper co the
     * nha hang tram ghe cua cung mot suat trong mot nhip.
     */
    private void baoSoDoGheDoi(List<GheDaNha> gheDaNha) {
        Map<String, List<String>> theoSuatChieu = gheDaNha.stream()
                .collect(Collectors.groupingBy(GheDaNha::showtimeId,
                        Collectors.mapping(GheDaNha::seatId, Collectors.toList())));

        theoSuatChieu.forEach((showtimeId, seatIds) -> {
            List<String> nhanGhe = jdbc.query(SQL_SEAT_LABELS,
                    new MapSqlParameterSource().addValue("seatIds", seatIds.toArray(String[]::new)),
                    (rs, n) -> rs.getString("label"));
            seatMapChannel.seatsChanged(UUID.fromString(showtimeId), "RELEASED", nhanGhe);
        });
    }

    private record GheDaNha(String showtimeId, String seatId) {
    }
}
