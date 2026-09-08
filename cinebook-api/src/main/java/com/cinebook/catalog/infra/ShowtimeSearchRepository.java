package com.cinebook.catalog.infra;

import com.cinebook.catalog.api.ShowtimeSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ShowtimeSearchRepository {

    /**
     * Moi bo loc deu tuy chon. Ky thuat ":param IS NULL OR cot = :param" giu SQL thanh
     * mot chuoi tinh duy nhat thay vi ghep dong theo dieu kien — de doc, de kiem tra
     * ke hoach thuc thi, va khong co duong nao de chen SQL.
     *
     * CAST(:param AS ...) la bat buoc: khi tham so la null, PostgreSQL khong suy duoc
     * kieu va bao "could not determine data type of parameter".
     *
     * LIMIT/OFFSET them o Milestone 10. Truoc do cau nay khong co gioi han nao, va voi
     * 200 475 dong no cho ra:
     *     Parallel Seq Scan + Sort Method: external merge  Disk: 4264kB
     *     Execution Time: 229 ms
     * cong voi 200 nghin ban ghi tuan tu hoa ra JSON — tren mot endpoint permitAll.
     * ShowtimePaginationTest giu lai gioi han do.
     */
    private static final String SQL = """
            SELECT s.id         AS showtime_id,
                   s.start_at,
                   s.base_price,
                   m.id         AS movie_id,
                   m.title      AS movie_title,
                   c.id         AS cinema_id,
                   c.name       AS cinema_name,
                   c.district,
                   r.id         AS room_id,
                   r.name       AS room_name
              FROM showtimes s
              JOIN movies  m ON m.id = s.movie_id
              JOIN rooms   r ON r.id = s.room_id
              JOIN cinemas c ON c.id = r.cinema_id
             WHERE s.status = 'SCHEDULED'
               AND (CAST(:movieId AS uuid) IS NULL OR s.movie_id = CAST(:movieId AS uuid))
               AND (CAST(:city     AS text) IS NULL OR c.city     = CAST(:city     AS text))
               AND (CAST(:district AS text) IS NULL OR c.district = CAST(:district AS text))
               AND (CAST(:from AS timestamptz) IS NULL OR s.start_at >= CAST(:from AS timestamptz))
               AND (CAST(:to   AS timestamptz) IS NULL OR s.start_at <= CAST(:to   AS timestamptz))
             ORDER BY s.start_at, c.name, r.name
             LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ShowtimeSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ShowtimeSummary> search(UUID movieId, String city, String district,
                                        Instant from, Instant to, int limit, int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset)
                .addValue("movieId", movieId == null ? null : movieId.toString())
                .addValue("city", city)
                .addValue("district", district)
                .addValue("from", from == null ? null : Timestamp.from(from))
                .addValue("to", to == null ? null : Timestamp.from(to));

        return jdbc.query(SQL, params, (rs, rowNum) -> new ShowtimeSummary(
                UUID.fromString(rs.getString("showtime_id")),
                rs.getTimestamp("start_at").toInstant(),
                UUID.fromString(rs.getString("movie_id")),
                rs.getString("movie_title"),
                UUID.fromString(rs.getString("cinema_id")),
                rs.getString("cinema_name"),
                rs.getString("district"),
                UUID.fromString(rs.getString("room_id")),
                rs.getString("room_name"),
                rs.getLong("base_price")));
    }
}
