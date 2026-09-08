package com.cinebook.catalog.infra;

import com.cinebook.catalog.api.PriceQuery;
import com.cinebook.catalog.api.SeatView;
import com.cinebook.catalog.api.ShowtimeDetail;
import com.cinebook.catalog.api.ShowtimeQuery;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Counter luotNapGhe;

    /**
     * So do ghe cua mot phong, giu lai theo room_id.
     *
     * An toan vi ghe la bat bien sau khi phong duoc tao: chung duoc sinh mot lan trong
     * POST /admin/cinemas/{id}/rooms va khong co endpoint nao sua, xoa, hay ghi is_active.
     * Phong moi mang uuid moi nen khong bao gio dung vao dong da cache.
     *
     * CHI cache phan tinh. Gia mot ghe = showtimes.base_price + phu phi theo loai, tuc no
     * phu thuoc SUAT CHIEU chu khong chi phu thuoc phong — cache ca gia se tra gia cua suat
     * dau tien cho moi suat sau do, mot loi tinh tien im lang. Test
     * cache_khong_lam_sai_gia_khi_hai_suat_chieu_khac_base_price giu lai su phan biet nay.
     */
    private final Map<UUID, List<GheTinh>> soDoGheTheoPhong = new ConcurrentHashMap<>();

    public ShowtimeQueryJdbc(NamedParameterJdbcTemplate jdbc, PriceQuery priceQuery,
                             MeterRegistry registry) {
        this.jdbc = jdbc;
        this.priceQuery = priceQuery;
        this.luotNapGhe = Counter.builder("cinebook.reference.load")
                .tag("bang", "seats")
                .description("So lan doc bang tra cuu tinh tu database")
                .register(registry);
    }

    /** Phan khong doi cua mot ghe. Gia khong nam o day, va do la chu y. */
    private record GheTinh(UUID id, String rowLabel, int seatNumber, String seatType) {
    }

    private List<GheTinh> gheCuaPhong(UUID roomId) {
        return soDoGheTheoPhong.computeIfAbsent(roomId, id -> {
            luotNapGhe.increment();
            List<GheTinh> ghe = new ArrayList<>();
            for (Map<String, Object> row : jdbc.queryForList(SQL_SEATS, Map.of("roomId", id))) {
                ghe.add(new GheTinh(
                        (UUID) row.get("id"),
                        (String) row.get("row_label"),
                        ((Number) row.get("seat_number")).intValue(),
                        (String) row.get("seat_type")));
            }
            return List.copyOf(ghe);
        });
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
        //
        // Ca hai nguon duoi day gio deu duoc giu trong bo nho, nen mot lan xem so do ghe
        // chi con HAI vong toi database thay vi bon (xem ghi chu o soDoGheTheoPhong).
        PriceQuery.BangGia bangGia = priceQuery.bangGia();

        List<SeatView> seats = new ArrayList<>();
        for (GheTinh ghe : gheCuaPhong(roomId)) {
            seats.add(new SeatView(
                    ghe.id(),
                    ghe.rowLabel(),
                    ghe.seatNumber(),
                    ghe.rowLabel() + ghe.seatNumber(),
                    ghe.seatType(),
                    bangGia.priceFor(basePrice, ghe.seatType())));
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
