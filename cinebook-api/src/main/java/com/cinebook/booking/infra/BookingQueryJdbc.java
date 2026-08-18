package com.cinebook.booking.infra;

import com.cinebook.booking.api.BookingView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BookingQueryJdbc {

    /**
     * Gop danh sach ghe bang array_agg thay vi truy van rieng cho tung don:
     * mot cau SQL duy nhat, khong co van de N+1.
     */
    private static final String SQL_BASE = """
            SELECT b.id           AS booking_id,
                   b.code,
                   b.status,
                   b.total_amount,
                   b.hold_expires_at,
                   b.created_at,
                   s.id           AS showtime_id,
                   s.start_at,
                   m.title        AS movie_title,
                   c.name         AS cinema_name,
                   COALESCE(
                       ARRAY_AGG(i.seat_label ORDER BY i.seat_label)
                           FILTER (WHERE i.seat_label IS NOT NULL),
                       ARRAY[]::varchar[]
                   ) AS seat_labels
              FROM bookings b
              JOIN showtimes s ON s.id = b.showtime_id
              JOIN movies    m ON m.id = s.movie_id
              JOIN rooms     r ON r.id = s.room_id
              JOIN cinemas   c ON c.id = r.cinema_id
              LEFT JOIN booking_items i ON i.booking_id = b.id
            """;

    private static final String SQL_BY_USER = SQL_BASE + """
             WHERE b.user_id = CAST(:userId AS uuid)
             GROUP BY b.id, s.id, m.title, c.name
             ORDER BY b.created_at DESC
            """;

    private static final String SQL_BY_ID = SQL_BASE + """
             WHERE b.id = CAST(:bookingId AS uuid)
             GROUP BY b.id, s.id, m.title, c.name
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BookingQueryJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BookingView> findByUser(UUID userId) {
        return jdbc.query(SQL_BY_USER,
                new MapSqlParameterSource().addValue("userId", userId.toString()),
                (rs, rowNum) -> map(rs));
    }

    public Optional<BookingView> findById(UUID bookingId) {
        return jdbc.query(SQL_BY_ID,
                        new MapSqlParameterSource().addValue("bookingId", bookingId.toString()),
                        (rs, rowNum) -> map(rs))
                .stream()
                .findFirst();
    }

    public Optional<UUID> findOwner(UUID bookingId) {
        return jdbc.query("SELECT user_id FROM bookings WHERE id = CAST(:bookingId AS uuid)",
                        new MapSqlParameterSource().addValue("bookingId", bookingId.toString()),
                        (rs, rowNum) -> UUID.fromString(rs.getString("user_id")))
                .stream()
                .findFirst();
    }

    private BookingView map(ResultSet rs) throws SQLException {
        Array array = rs.getArray("seat_labels");
        String[] seats = array == null ? new String[0] : (String[]) array.getArray();

        return new BookingView(
                UUID.fromString(rs.getString("booking_id")),
                rs.getString("code"),
                rs.getString("status"),
                rs.getLong("total_amount"),
                rs.getTimestamp("hold_expires_at") == null
                        ? null : rs.getTimestamp("hold_expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                UUID.fromString(rs.getString("showtime_id")),
                rs.getString("movie_title"),
                rs.getString("cinema_name"),
                rs.getTimestamp("start_at").toInstant(),
                List.of(seats));
    }
}
