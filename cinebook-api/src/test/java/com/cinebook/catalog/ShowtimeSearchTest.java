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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShowtimeSearchTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";
    private static final Instant TOI = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired
    JdbcTemplate db;

    private String tokenAdmin;
    private String phimHanhDong;
    private String phimHai;

    @BeforeEach
    void reset() {
        truncate("showtimes");
        truncate("seats", "rooms", "cinemas");
        truncate("movie_genres", "movies");
        truncate("users");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        tokenAdmin = login("admin@example.com");

        phimHanhDong = createMovie("Phim Hanh Dong", 120);
        phimHai = createMovie("Phim Hai", 100);

        String rapQ1 = createCinema("CGV Quan 1", "Quan 1");
        String rapQ7 = createCinema("CGV Quan 7", "Quan 7");
        String phongQ1 = createRoom(rapQ1, "Phong 1");
        String phongQ7 = createRoom(rapQ7, "Phong 1");

        createShowtime(phimHanhDong, phongQ1, TOI);
        createShowtime(phimHai, phongQ1, TOI.plus(4, ChronoUnit.HOURS));
        createShowtime(phimHanhDong, phongQ7, TOI);
    }

    @Test
    void khong_co_bo_loc_thi_tra_ve_tat_ca_suat_chieu() {
        var body = search(Map.of());

        assertThat(body.size()).isEqualTo(3);
    }

    @Test
    void loc_theo_quan_chi_tra_ve_suat_o_quan_do() {
        var body = search(Map.of("city", "Ho Chi Minh", "district", "Quan 1"));

        assertThat(body.size()).isEqualTo(2);
        for (JsonNode item : body) {
            assertThat(item.get("district").asText()).isEqualTo("Quan 1");
        }
    }

    @Test
    void loc_theo_phim_va_quan_cung_luc() {
        var body = search(Map.of(
                "movieId", phimHanhDong,
                "city", "Ho Chi Minh",
                "district", "Quan 1"));

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("movieTitle").asText()).isEqualTo("Phim Hanh Dong");
        assertThat(body.get(0).get("cinemaName").asText()).isEqualTo("CGV Quan 1");
    }

    @Test
    void loc_theo_khoang_thoi_gian() {
        var body = search(Map.of(
                "from", TOI.plus(3, ChronoUnit.HOURS).toString(),
                "to", TOI.plus(6, ChronoUnit.HOURS).toString()));

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("movieTitle").asText()).isEqualTo("Phim Hai");
    }

    /**
     * Dung uri builder chu khong noi chuoi: gia tri nhu "Ho Chi Minh" va "Quan 1"
     * co dau cach, ghep thang vao URL se lam URI khong hop le.
     */
    private JsonNode search(Map<String, String> params) {
        var response = client().get()
                .uri(builder -> {
                    builder.path("/showtimes");
                    params.forEach((name, value) -> builder.queryParam(name, value));
                    return builder.build();
                })
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String createMovie(String title, int durationMin) {
        return client().post()
                .uri("/admin/movies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "originalTitle", title,
                        "description", "Mo ta", "durationMin", durationMin,
                        "genres", List.of("Hanh dong"), "ageRating", "T16",
                        "posterUrl", "https://example.com/p.jpg",
                        "trailerUrl", "https://example.com/t.mp4",
                        "releaseDate", "2026-08-01"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody().get("id").asText();
    }

    private String createCinema(String name, String district) {
        return client().post()
                .uri("/admin/cinemas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "address", "123 ABC",
                        "district", district, "city", "Ho Chi Minh"))
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

    private void createShowtime(String movieId, String roomId, Instant startAt) {
        client().post()
                .uri("/admin/showtimes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("movieId", movieId, "roomId", roomId,
                        "startAt", startAt.toString(), "basePrice", 90000))
                .retrieve()
                .toBodilessEntity();
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
