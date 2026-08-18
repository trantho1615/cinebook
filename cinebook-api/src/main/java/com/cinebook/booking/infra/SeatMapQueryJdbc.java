package com.cinebook.booking.infra;

import com.cinebook.booking.api.SeatMapEntry;
import com.cinebook.booking.api.SeatStatus;
import com.cinebook.catalog.api.SeatView;
import com.cinebook.catalog.api.ShowtimeDetail;
import com.cinebook.catalog.api.ShowtimeQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ghep so do ghe TINH cua catalog voi trang thai giu cho DONG cua booking.
 *
 * Chi lay hold con hieu luc: dieu kien expires_at > now() khien hold da qua han
 * hien ra AVAILABLE ngay lap tuc, khong phai cho sweeper chay. Day la mat doc
 * cua co che lazy expiration.
 */
@Component
public class SeatMapQueryJdbc {

    private static final String SQL_ACTIVE_HOLDS = """
            SELECT seat_id, status
              FROM seat_hold
             WHERE showtime_id = CAST(:showtimeId AS uuid)
               AND (status = 'BOOKED'
                    OR (status = 'HELD' AND expires_at > now()))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ShowtimeQuery showtimeQuery;

    public SeatMapQueryJdbc(NamedParameterJdbcTemplate jdbc, ShowtimeQuery showtimeQuery) {
        this.jdbc = jdbc;
        this.showtimeQuery = showtimeQuery;
    }

    public Optional<List<SeatMapEntry>> seatMap(UUID showtimeId) {
        Optional<ShowtimeDetail> detail = showtimeQuery.findDetail(showtimeId);
        if (detail.isEmpty()) {
            return Optional.empty();
        }

        Map<UUID, SeatStatus> trangThai = new HashMap<>();
        jdbc.query(SQL_ACTIVE_HOLDS,
                new MapSqlParameterSource().addValue("showtimeId", showtimeId.toString()),
                rs -> {
                    trangThai.put(UUID.fromString(rs.getString("seat_id")),
                            SeatStatus.valueOf(rs.getString("status")));
                });

        List<SeatMapEntry> ketQua = detail.get().seats().stream()
                .map(seat -> toEntry(seat, trangThai.getOrDefault(seat.seatId(), SeatStatus.AVAILABLE)))
                .toList();

        return Optional.of(ketQua);
    }

    private SeatMapEntry toEntry(SeatView seat, SeatStatus status) {
        return new SeatMapEntry(seat.seatId(), seat.rowLabel(), seat.seatNumber(),
                seat.label(), seat.seatType(), seat.price(), status);
    }
}
