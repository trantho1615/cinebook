package com.cinebook.payment;

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
 * Khong tai su dung BookingFixture cua Milestone 4 vi no package-private trong
 * package booking cua test.
 */
final class PaymentFixture {

    private static final String PASSWORD = "MatKhauRatManh123";

    private final AbstractApiTest test;
    private final String showtimeId;
    private final String tokenA;
    private final String tokenB;
    private final Map<String, String> seatIdByLabel;

    private PaymentFixture(AbstractApiTest test, String showtimeId, String tokenA,
                           String tokenB, Map<String, String> seatIdByLabel) {
        this.test = test;
        this.showtimeId = showtimeId;
        this.tokenA = tokenA;
        this.tokenB = tokenB;
        this.seatIdByLabel = seatIdByLabel;
    }

    static PaymentFixture freshSetup(AbstractApiTest test, JdbcTemplate db) {
        db.execute("TRUNCATE TABLE payment_events, payments CASCADE");
        db.execute("TRUNCATE TABLE audit_log, booking_items, seat_hold, bookings CASCADE");
        db.execute("TRUNCATE TABLE idempotency_keys CASCADE");
        db.execute("TRUNCATE TABLE showtimes CASCADE");
        db.execute("TRUNCATE TABLE seats, rooms, cinemas CASCADE");
        db.execute("TRUNCATE TABLE movie_genres, movies CASCADE");
        db.execute("TRUNCATE TABLE users CASCADE");

        register(test, "a@example.com");
        String tokenA = login(test, "a@example.com");
        register(test, "b@example.com");
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

        return new PaymentFixture(test, showtimeId, tokenA, tokenB, seatIdByLabel);
    }

    String tokenA() {
        return tokenA;
    }

    String tokenB() {
        return tokenB;
    }

    String showtimeId() {
        return showtimeId;
    }

    /**
     * Giu ghe va tra ve bookingId — buoc mo dau cua moi test trong milestone nay.
     */
    String holdSeats(String token, String... labels) {
        List<String> seatIds = List.of(labels).stream().map(seatIdByLabel::get).toList();
        return post(test, token, "/showtimes/" + showtimeId + "/holds",
                Map.of("seatIds", seatIds)).get("bookingId").asText();
    }

    void initiatePayment(AbstractApiTest t, String token, String bookingId) {
        post(t, token, "/bookings/" + bookingId + "/payments", Map.of());
    }

    /**
     * Day gio he thong ve qua khu thay vi cho 10 phut. Test khong duoc cho doi.
     */
    void lamChoHetHan(JdbcTemplate db) {
        db.update("UPDATE seat_hold SET expires_at = now() - interval '1 minute' WHERE status = 'HELD'");
        db.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE status = 'PENDING'");
    }

    private static void register(AbstractApiTest test, String email) {
        post(test, null, "/auth/register", Map.of(
                "email", email, "password", PASSWORD,
                "fullName", "Nguoi Dung", "phone", "0900000000"));
    }

    private static String login(AbstractApiTest test, String email) {
        return post(test, null, "/auth/login", Map.of("email", email, "password", PASSWORD))
                .get("accessToken").asText();
    }

    private static JsonNode post(AbstractApiTest test, String token, String uri, Map<String, ?> body) {
        var spec = test.client().post().uri(uri).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.body(body).retrieve().toEntity(JsonNode.class).getBody();
    }
}
