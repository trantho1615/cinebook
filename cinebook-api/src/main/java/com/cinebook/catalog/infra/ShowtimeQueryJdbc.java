package com.cinebook.catalog.infra;

import com.cinebook.catalog.api.PriceQuery;
import com.cinebook.catalog.api.SeatView;
import com.cinebook.catalog.api.ShowtimeDetail;
import com.cinebook.catalog.api.ShowtimeQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model duoc lay bang JDBC thuan chu khong bang JPA: day la duong doc nong
 * (spec muc 6.2.1), va mot cau SQL de doc va de do hon la mot chuoi quan he JPA
 * voi lazy loading kho kiem soat.
 */
@Component
public class ShowtimeQueryJdbc implements ShowtimeQuery {

    private static final String SQL_HEADER = """
            SELECT s.id           AS showtime_id,
                   s.start_at,
                   s.end_at,
                   s.status,
                   s.base_price,
                   m.id           AS movie_id,
                   m.title        AS movie_title,
                   m.duration_min,
                   m.age_rating,
                   c.id           AS cinema_id,
                   c.name         AS cinema_name,
                   r.id           AS room_id,
                   r.name         AS room_name
              FROM showtimes s
              JOIN movies  m ON m.id = s.movie_id
              JOIN rooms   r ON r.id = s.room_id
              JOIN cinemas c ON c.id = r.cinema_id
             WHERE s.id = :showtimeId
            """;

    private static final String SQL_SEATS = """
            SELECT id, row_label, seat_number, seat_type
              FROM seats
             WHERE room_id = :roomId
               AND is_active = true
             ORDER BY row_label, seat_number
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PriceQuery priceQuery;

    public ShowtimeQueryJdbc(NamedParameterJdbcTemplate jdbc, PriceQuery priceQuery) {
        this.jdbc = jdbc;
        this.priceQuery = priceQuery;
    }

    @Override
    public Optional<ShowtimeDetail> findDetail(UUID showtimeId) {
        List<Map<String, Object>> headers =
                jdbc.queryForList(SQL_HEADER, Map.of("showtimeId", showtimeId));
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> h = headers.getFirst();

        UUID roomId = (UUID) h.get("room_id");
        long basePrice = ((Number) h.get("base_price")).longValue();

        // Hai cau SQL chu khong phai mot: gop so do ghe vao cung cau voi phan dau se
        // nhan ban toan bo thong tin phim va rap len 50-200 dong.
        List<SeatView> seats = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(SQL_SEATS, Map.of("roomId", roomId))) {
            String rowLabel = (String) row.get("row_label");
            int seatNumber = ((Number) row.get("seat_number")).intValue();
            String seatType = (String) row.get("seat_type");
            seats.add(new SeatView(
                    (UUID) row.get("id"),
                    rowLabel,
                    seatNumber,
                    rowLabel + seatNumber,
                    seatType,
                    priceQuery.priceFor(basePrice, seatType)));
        }

        return Optional.of(new ShowtimeDetail(
                (UUID) h.get("showtime_id"),
                ((Timestamp) h.get("start_at")).toInstant(),
                ((Timestamp) h.get("end_at")).toInstant(),
                (String) h.get("status"),
                (UUID) h.get("movie_id"),
                (String) h.get("movie_title"),
                ((Number) h.get("duration_min")).intValue(),
                (String) h.get("age_rating"),
                (UUID) h.get("cinema_id"),
                (String) h.get("cinema_name"),
                roomId,
                (String) h.get("room_name"),
                basePrice,
                List.copyOf(seats)));
    }
}
