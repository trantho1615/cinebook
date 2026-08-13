package com.cinebook.catalog;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShowtimeTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";
    private static final Instant TOI_MAI = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired
    JdbcTemplate db;

    private String tokenAdmin;
    private String movieId;      // phim dai 120 phut
    private String roomId;
    private String roomKhacId;

    @BeforeEach
    void reset() {
        truncate("showtimes");
        truncate("seats", "rooms", "cinemas");
        truncate("movie_genres", "movies");
        truncate("users");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        tokenAdmin = login("admin@example.com");

        movieId = createMovie(120);
        String cinemaId = createCinema();
        roomId = createRoom(cinemaId, "Phong 1");
        roomKhacId = createRoom(cinemaId, "Phong 2");
    }

    @Test
    void tao_suat_chieu_thi_gio_ket_thuc_duoc_tinh_tu_thoi_luong_phim_cong_15_phut_don_dep() {
        var response = createShowtime(roomId, TOI_MAI);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Instant endAt = db.queryForObject(
                "SELECT end_at FROM showtimes", java.sql.Timestamp.class).toInstant();
        // 120 phut phim + 15 phut don dep
        assertThat(endAt).isEqualTo(TOI_MAI.plus(135, ChronoUnit.MINUTES));
    }

    @Test
    void xep_hai_suat_chong_gio_trong_cung_phong_bi_tu_choi_409() {
        createShowtime(roomId, TOI_MAI);

        // Bat dau sau 60 phut, trong khi suat truoc keo dai 135 phut
        var response = createShowtime(roomId, TOI_MAI.plus(60, ChronoUnit.MINUTES));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("SHOWTIME_OVERLAP");
        assertThat(db.queryForObject("SELECT count(*) FROM showtimes", Integer.class)).isEqualTo(1);
    }

    @Test
    void suat_sau_bat_dau_dung_luc_suat_truoc_ket_thuc_thi_duoc_phep() {
        createShowtime(roomId, TOI_MAI);

        var response = createShowtime(roomId, TOI_MAI.plus(135, ChronoUnit.MINUTES));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(db.queryForObject("SELECT count(*) FROM showtimes", Integer.class)).isEqualTo(2);
    }

    @Test
    void cung_khung_gio_nhung_khac_phong_thi_duoc_phep() {
        createShowtime(roomId, TOI_MAI);

        var response = createShowtime(roomKhacId, TOI_MAI);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(db.queryForObject("SELECT count(*) FROM showtimes", Integer.class)).isEqualTo(2);
    }

    @Test
    void suat_da_huy_khong_chan_viec_xep_suat_moi_vao_dung_khung_gio_do() {
        createShowtime(roomId, TOI_MAI);
        db.update("UPDATE showtimes SET status = 'CANCELLED'");

        var response = createShowtime(roomId, TOI_MAI);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<JsonNode> createShowtime(String room, Instant startAt) {
        return client().post()
                .uri("/admin/showtimes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("movieId", movieId, "roomId", room,
                        "startAt", startAt.toString(), "basePrice", 90000))
                .retrieve()
                .toEntity(JsonNode.class);
    }

    private String createMovie(int durationMin) {
        return client().post()
                .uri("/admin/movies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Phim Test", "originalTitle", "Test Movie",
                        "description", "Mo ta", "durationMin", durationMin,
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

    private String createRoom(String cinemaId, String name) {
        return client().post()
                .uri("/admin/cinemas/" + cinemaId + "/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "roomType", "STANDARD",
                        "rowCount", 5, "seatsPerRow", 10))
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
