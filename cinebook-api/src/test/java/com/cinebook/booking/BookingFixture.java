package com.cinebook.booking;

import com.cinebook.support.AbstractApiTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dung san mot suat chieu that (phim 120 phut, phong 5 hang x 10 ghe, gia goc 90000)
 * cung hai nguoi dung thuong va mot admin.
 *
 * Moi test class cua milestone nay deu can dung bo du lieu nay, nen tach ra day
 * thay vi chep lai o sau cho.
 */
/**
 * Public tu Milestone 6: test cua notification o package khac cung can dung mot suat
 * chieu that voi ghe that. Chi lop nay va cac phuong thuc doc duoc mo, khong mo gi them.
 */
public final class BookingFixture {

    private static final String PASSWORD = "MatKhauRatManh123";

    private final String showtimeId;
    private final String tokenA;
    private final String tokenB;
    private final String userIdA;
    private final String userIdB;
    private final Map<String, String> seatIdByLabel;

    private BookingFixture(String showtimeId, String tokenA, String tokenB,
                           String userIdA, String userIdB, Map<String, String> seatIdByLabel) {
        this.showtimeId = showtimeId;
        this.tokenA = tokenA;
        this.tokenB = tokenB;
        this.userIdA = userIdA;
        this.userIdB = userIdB;
        this.seatIdByLabel = seatIdByLabel;
    }

    public static BookingFixture freshSetup(AbstractApiTest test, JdbcTemplate db) {
        db.execute("TRUNCATE TABLE outbox_events, audit_log, booking_items, seat_hold, bookings CASCADE");
        db.execute("TRUNCATE TABLE showtimes CASCADE");
        db.execute("TRUNCATE TABLE seats, rooms, cinemas CASCADE");
        db.execute("TRUNCATE TABLE movie_genres, movies CASCADE");
        db.execute("TRUNCATE TABLE users CASCADE");

        String userIdA = register(test, "a@example.com");
        String tokenA = login(test, "a@example.com");
        String userIdB = register(test, "b@example.com");
        String tokenB = login(test, "b@example.com");

        register(test, "admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        String tokenAdmin = login(test, "admin@example.com");

        String movieId = post(test, tokenAdmin, "/admin/movies", Map.of(
                "title", "Phim Test", "originalTitle", "Test", "description", "Mo ta",
                "durationMin", 120, "genres", List.of("Hanh dong"), "ageRating", "T16",
                "posterUrl", "https://example.com/p.jpg",
                "trailerUrl", "https://example.com/t.mp4",
                "releaseDate", "2026-08-01")).get("id").asText();

        String cinemaId = post(test, tokenAdmin, "/admin/cinemas", Map.of(
                "name", "CGV Quan 1", "address", "123 ABC",
                "district", "Quan 1", "city", "Ho Chi Minh")).get("id").asText();

        String roomId = post(test, tokenAdmin, "/admin/cinemas/" + cinemaId + "/rooms", Map.of(
                "name", "Phong 1", "roomType", "STANDARD",
                "rowCount", 5, "seatsPerRow", 10)).get("id").asText();

        // Suat chieu trong tuong lai de khong bi chan boi luat "suat da bat dau"
        String showtimeId = post(test, tokenAdmin, "/admin/showtimes", Map.of(
                "movieId", movieId, "roomId", roomId,
                "startAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "basePrice", 90000)).get("id").asText();

        Map<String, String> seatIdByLabel = new HashMap<>();
        db.query("SELECT id, row_label, seat_number FROM seats WHERE room_id = ?::uuid",
                rs -> {
                    seatIdByLabel.put(rs.getString("row_label") + rs.getInt("seat_number"),
                            rs.getString("id"));
                }, roomId);

        return new BookingFixture(showtimeId, tokenA, tokenB, userIdA, userIdB, seatIdByLabel);
    }

    public String showtimeId() {
        return showtimeId;
    }

    public String tokenA() {
        return tokenA;
    }

    public String tokenB() {
        return tokenB;
    }

    public String userIdA() {
        return userIdA;
    }

    public String userIdB() {
        return userIdB;
    }

    /**
     * Doi nhan ghe nguoi doc duoc ("A1", "C7") thanh UUID that.
     */
    public List<String> seatIds(String... labels) {
        return List.of(labels).stream()
                .map(label -> {
                    String id = seatIdByLabel.get(label);
                    if (id == null) {
                        throw new IllegalArgumentException("Khong co ghe " + label);
                    }
                    return id;
                })
                .toList();
    }

    private static String register(AbstractApiTest test, String email) {
        return post(test, null, "/auth/register", Map.of(
                "email", email, "password", PASSWORD,
                "fullName", "Nguoi Dung", "phone", "0900000000")).get("id").asText();
    }

    private static String login(AbstractApiTest test, String email) {
        return post(test, null, "/auth/login", Map.of("email", email, "password", PASSWORD))
                .get("accessToken").asText();
    }

    private static JsonNode post(AbstractApiTest test, String token, String uri, Map<String, ?> body) {
        var spec = test.client().post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.body(body).retrieve().toEntity(JsonNode.class).getBody();
    }
}
