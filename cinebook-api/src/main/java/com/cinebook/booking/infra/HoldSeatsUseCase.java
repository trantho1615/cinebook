package com.cinebook.booking.infra;

import com.cinebook.booking.domain.BookingCode;
import com.cinebook.booking.domain.InvalidSeatSelectionException;
import com.cinebook.booking.domain.SeatsUnavailableException;
import com.cinebook.booking.domain.ShowtimeNotBookableException;
import com.cinebook.catalog.api.SeatView;
import com.cinebook.catalog.api.ShowtimeDetail;
import com.cinebook.catalog.api.ShowtimeQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Giu ghe cho mot suat chieu.
 *
 * VIET BANG JDBC THUAN, KHONG DUNG JPA. Hibernate ActionQueue flush theo thu tu
 * "moi INSERT truoc moi UPDATE", ma transaction nay bat dau bang UPDATE (don hold
 * het han) roi moi INSERT. Dung JPA thi thu tu bi dao nguoc va co che het han
 * chet lang le: ghe da qua han khong bao gio duoc nguoi sau lay lai.
 */
@Component
public class HoldSeatsUseCase {

    /**
     * Nguoi den sau don cho cua hold da het han — UPDATE chu khong DELETE de giu
     * lich su phuc vu phan tich ti le HELD -> BOOKED.
     *
     * BUOC NAY BAT BUOC: unique index chi nhin status, khong biet expires_at, nen
     * hold qua han van chiem cho toi khi doi trang thai. Da kiem chung bang thuc nghiem.
     */
    private static final String SQL_RELEASE_EXPIRED = """
            UPDATE seat_hold
               SET status = 'EXPIRED', released_at = now(), release_reason = 'TAKEN_OVER'
             WHERE showtime_id = CAST(:showtimeId AS uuid)
               AND seat_id = ANY (CAST(:seatIds AS uuid[]))
               AND status = 'HELD'
               AND expires_at <= now()
            """;

    private static final String SQL_INSERT_BOOKING = """
            INSERT INTO bookings
                (id, code, user_id, showtime_id, status, total_amount, hold_expires_at, created_at)
            VALUES
                (CAST(:id AS uuid), :code, CAST(:userId AS uuid), CAST(:showtimeId AS uuid),
                 'PENDING', :totalAmount, :holdExpiresAt, :createdAt)
            """;

    /**
     * ORDER BY s.seat_id la thu chong deadlock: hai transaction cung giu {F7, F8} theo
     * thu tu nguoc nhau se cho nhau vinh vien. Sap xep khien moi transaction khoa
     * theo cung mot thu tu. DUNG BO DONG NAY.
     *
     * ON CONFLICT DO NOTHING + RETURNING: khong nem exception, chi tra ve ghe nao
     * vao duoc. So sanh so luong la biet chinh xac ghe nao mat.
     */
    private static final String SQL_INSERT_HOLDS = """
            INSERT INTO seat_hold
                (id, showtime_id, seat_id, booking_id, user_id, status, expires_at, created_at)
            SELECT gen_random_uuid(), CAST(:showtimeId AS uuid), s.seat_id,
                   CAST(:bookingId AS uuid), CAST(:userId AS uuid), 'HELD',
                   :expiresAt, :createdAt
              FROM unnest(CAST(:seatIds AS uuid[])) AS s(seat_id)
             ORDER BY s.seat_id
                ON CONFLICT (showtime_id, seat_id) WHERE status IN ('HELD', 'BOOKED')
                DO NOTHING
             RETURNING seat_id
            """;

    private static final String SQL_INSERT_ITEM = """
            INSERT INTO booking_items (id, booking_id, seat_id, seat_label, unit_price)
            VALUES (gen_random_uuid(), CAST(:bookingId AS uuid), CAST(:seatId AS uuid),
                    :seatLabel, :unitPrice)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ShowtimeQuery showtimeQuery;
    private final AuditLogger auditLogger;
    private final Duration holdTtl;
    private final int maxSeatsPerHold;

    public HoldSeatsUseCase(NamedParameterJdbcTemplate jdbc,
                            ShowtimeQuery showtimeQuery,
                            AuditLogger auditLogger,
                            @Value("${cinebook.booking.hold-ttl}") Duration holdTtl,
                            @Value("${cinebook.booking.max-seats-per-hold}") int maxSeatsPerHold) {
        this.jdbc = jdbc;
        this.showtimeQuery = showtimeQuery;
        this.auditLogger = auditLogger;
        this.holdTtl = holdTtl;
        this.maxSeatsPerHold = maxSeatsPerHold;
    }

    @Transactional
    public HoldResult hold(UUID userId, UUID showtimeId, List<UUID> requestedSeatIds) {
        validateSelectionSize(requestedSeatIds);

        ShowtimeDetail showtime = showtimeQuery.findDetail(showtimeId)
                .orElseThrow(() -> new ShowtimeNotBookableException("Suat chieu khong ton tai"));
        validateShowtimeBookable(showtime);

        Map<UUID, SeatView> seatsById = indexSeats(showtime);
        List<SeatView> chosen = resolveChosenSeats(requestedSeatIds, seatsById);

        // Sap xep de bao dam thu tu khoa nhat quan giua moi transaction.
        String[] sortedSeatIds = chosen.stream()
                .map(SeatView::seatId)
                .sorted()
                .map(UUID::toString)
                .toArray(String[]::new);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(holdTtl);

        // (a) Don cho cua hold da het han.
        jdbc.update(SQL_RELEASE_EXPIRED, new MapSqlParameterSource()
                .addValue("showtimeId", showtimeId.toString())
                .addValue("seatIds", sortedSeatIds));

        // (b) Tao booking TRUOC vi seat_hold.booking_id tham chieu toi no.
        UUID bookingId = UUID.randomUUID();
        String code = BookingCode.generate();
        long totalAmount = chosen.stream().mapToLong(SeatView::price).sum();

        jdbc.update(SQL_INSERT_BOOKING, new MapSqlParameterSource()
                .addValue("id", bookingId.toString())
                .addValue("code", code)
                .addValue("userId", userId.toString())
                .addValue("showtimeId", showtimeId.toString())
                .addValue("totalAmount", totalAmount)
                .addValue("holdExpiresAt", Timestamp.from(expiresAt))
                .addValue("createdAt", Timestamp.from(now)));

        // (c) Giu tat ca ghe trong mot cau, all-or-nothing.
        List<UUID> acquired = jdbc.query(SQL_INSERT_HOLDS, new MapSqlParameterSource()
                        .addValue("showtimeId", showtimeId.toString())
                        .addValue("bookingId", bookingId.toString())
                        .addValue("userId", userId.toString())
                        .addValue("seatIds", sortedSeatIds)
                        .addValue("expiresAt", Timestamp.from(expiresAt))
                        .addValue("createdAt", Timestamp.from(now)),
                (rs, rowNum) -> UUID.fromString(rs.getString("seat_id")));

        if (acquired.size() != chosen.size()) {
            Set<UUID> acquiredSet = new HashSet<>(acquired);
            List<String> taken = chosen.stream()
                    .filter(seat -> !acquiredSet.contains(seat.seatId()))
                    .map(SeatView::label)
                    .toList();
            // Nem exception -> transaction rollback -> booking vua tao cung bien mat.
            throw new SeatsUnavailableException(taken);
        }

        // (d) Chot gia tai thoi diem giu ghe.
        for (SeatView seat : chosen) {
            jdbc.update(SQL_INSERT_ITEM, new MapSqlParameterSource()
                    .addValue("bookingId", bookingId.toString())
                    .addValue("seatId", seat.seatId().toString())
                    .addValue("seatLabel", seat.label())
                    .addValue("unitPrice", seat.price()));
        }

        auditLogger.record("BOOKING", bookingId, "SEATS_HELD",
                AuditLogger.ActorType.USER, userId,
                "{\"seatCount\":" + chosen.size() + ",\"totalAmount\":" + totalAmount + "}");

        return new HoldResult(bookingId, code, totalAmount, expiresAt,
                chosen.stream().map(SeatView::label).toList());
    }

    private void validateSelectionSize(List<UUID> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new InvalidSeatSelectionException("Phai chon it nhat mot ghe");
        }
        if (seatIds.size() > maxSeatsPerHold) {
            throw new InvalidSeatSelectionException(
                    "Moi lan chi duoc giu toi da " + maxSeatsPerHold + " ghe");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new InvalidSeatSelectionException("Danh sach ghe co phan tu trung lap");
        }
    }

    private void validateShowtimeBookable(ShowtimeDetail showtime) {
        if (!"SCHEDULED".equals(showtime.status())) {
            throw new ShowtimeNotBookableException("Suat chieu da bi huy");
        }
        if (!showtime.startAt().isAfter(Instant.now())) {
            throw new ShowtimeNotBookableException("Suat chieu da bat dau");
        }
    }

    private Map<UUID, SeatView> indexSeats(ShowtimeDetail showtime) {
        Map<UUID, SeatView> byId = new LinkedHashMap<>();
        for (SeatView seat : showtime.seats()) {
            byId.put(seat.seatId(), seat);
        }
        return byId;
    }

    private List<SeatView> resolveChosenSeats(List<UUID> requested, Map<UUID, SeatView> seatsById) {
        List<SeatView> chosen = new ArrayList<>(requested.size());
        for (UUID seatId : requested) {
            SeatView seat = seatsById.get(seatId);
            if (seat == null) {
                // Ghe khong thuoc phong cua suat chieu nay.
                throw new InvalidSeatSelectionException("Ghe khong thuoc suat chieu nay: " + seatId);
            }
            chosen.add(seat);
        }
        List<SeatView> sorted = new ArrayList<>(chosen);
        sorted.sort(Comparator.comparing(SeatView::rowLabel).thenComparingInt(SeatView::seatNumber));
        return List.copyOf(sorted);
    }

    public record HoldResult(UUID bookingId, String code, long totalAmount,
                             Instant holdExpiresAt, List<String> seatLabels) {
    }
}
