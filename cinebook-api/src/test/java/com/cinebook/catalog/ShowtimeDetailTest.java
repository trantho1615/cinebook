package com.cinebook.catalog;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShowtimeDetailTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";
    private static final Instant START = Instant.parse("2026-09-01T12:00:00Z");
    private static final long BASE_PRICE = 90000L;

    @Autowired
    JdbcTemplate db;

    private String tokenAdmin;
    private String showtimeId;

    @BeforeEach
    void reset() {
        truncate("showtimes");
        truncate("seats", "rooms", "cinemas");
        truncate("movie_genres", "movies");
        truncate("users");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        tokenAdmin = login("admin@example.com");

        String movieId = createMovie();
        String cinemaId = createCinema();
        String roomId = createRoom(cinemaId);
        showtimeId = createShowtime(movieId, roomId);
    }

    @Test
    void tra_ve_day_du_suat_chieu_phim_rap_phong_va_so_do_ghe_trong_mot_lan_goi() {
        var response = client().get()
                .uri("/showtimes/" + showtimeId)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();

        assertThat(body.get("movieTitle").asText()).isEqualTo("Phim Test");
        assertThat(body.get("durationMin").asInt()).isEqualTo(120);
        assertThat(body.get("cinemaName").asText()).isEqualTo("CGV Quan 1");
        assertThat(body.get("roomName").asText()).isEqualTo("Phong 1");
        assertThat(body.get("basePrice").asLong()).isEqualTo(BASE_PRICE);
        assertThat(body.get("seats").size()).isEqualTo(50);   // 5 hang x 10 ghe
    }

    @Test
    void gia_tung_ghe_bang_gia_co_ban_cong_phu_phi_theo_loai_ghe() {
        var body = client().get()
                .uri("/showtimes/" + showtimeId)
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();

        long giaStandard = giaCuaHang(body, "A");
        long giaVip = giaCuaHang(body, "C");
        long giaCouple = giaCuaHang(body, "E");

        assertThat(giaStandard).isEqualTo(BASE_PRICE);
        assertThat(giaVip).isEqualTo(BASE_PRICE + 20000);
        assertThat(giaCouple).isEqualTo(BASE_PRICE + 50000);
    }

    @Test
    void so_do_ghe_duoc_sap_xep_theo_hang_roi_theo_so_ghe() {
        var seats = client().get()
                .uri("/showtimes/" + showtimeId)
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody()
                .get("seats");

        assertThat(seats.get(0).get("label").asText()).isEqualTo("A1");
        assertThat(seats.get(1).get("label").asText()).isEqualTo("A2");
        assertThat(seats.get(49).get("label").asText()).isEqualTo("E10");
    }

    @Test
    void suat_chieu_khong_ton_tai_tra_404() {
        var response = client().get()
                .uri("/showtimes/00000000-0000-0000-0000-000000000000")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private long giaCuaHang(JsonNode body, String rowLabel) {
        for (JsonNode seat : body.get("seats")) {
            if (seat.get("rowLabel").asText().equals(rowLabel)) {
                return seat.get("price").asLong();
            }
        }
        throw new AssertionError("Khong tim thay hang " + rowLabel);
    }

    private String createMovie() {
        return client().post()
                .uri("/admin/movies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Phim Test", "originalTitle", "Test Movie",
                        "description", "Mo ta", "durationMin", 120,
                        "genres", List.of("Hanh dong"), "ageRating", "T16",
                        "posterUrl", "https://example.com/p.jpg",
                        "trailerUrl", "https://example.com/t.mp4",
                        "releaseDate", "2026-08-01"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody().get("id").asText();
    }

    private String createCinema() {
        return client().post()
                .uri("/admin/cinemas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "CGV Quan 1", "address", "123 ABC",
                        "district", "Quan 1", "city", "Ho Chi Minh"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody().get("id").asText();
    }

    private String createRoom(String cinemaId) {
        return client().post()
                .uri("/admin/cinemas/" + cinemaId + "/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Phong 1", "roomType", "STANDARD",
                        "rowCount", 5, "seatsPerRow", 10))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody().get("id").asText();
    }

    private String createShowtime(String movieId, String roomId) {
        return client().post()
                .uri("/admin/showtimes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("movieId", movieId, "roomId", roomId,
                        "startAt", START.toString(), "basePrice", BASE_PRICE))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody().get("id").asText();
    }

    private void register(String email) {
        client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD,
                        "fullName", "Nguoi Dung", "phone", "0900000000"))
                .retrieve()
                .toBodilessEntity();
    }

    private String login(String email) {
        return client().post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody().get("accessToken").asText();
    }
}
