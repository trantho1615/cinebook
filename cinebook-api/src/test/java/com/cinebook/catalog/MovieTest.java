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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MovieTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";

    @Autowired
    JdbcTemplate db;

    private String tokenAdmin;
    private String tokenCustomer;

    @BeforeEach
    void reset() {
        truncate("movie_genres", "movies");
        truncate("users");

        register("khach@example.com");
        tokenCustomer = login("khach@example.com");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        tokenAdmin = login("admin@example.com");
    }

    @Test
    void admin_tao_duoc_phim_va_the_loai_duoc_luu_day_du() {
        var response = createMovie(tokenAdmin, "Fast and Furious 12", 128, List.of("Hanh dong", "Phieu luu"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("COMING_SOON");

        Integer soTheLoai = db.queryForObject("SELECT count(*) FROM movie_genres", Integer.class);
        assertThat(soTheLoai).isEqualTo(2);
    }

    @Test
    void khach_khong_tao_duoc_phim() {
        var response = createMovie(tokenCustomer, "Phim Lau", 100, List.of("Hai"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(db.queryForObject("SELECT count(*) FROM movies", Integer.class)).isZero();
    }

    @Test
    void ai_cung_xem_duoc_danh_sach_phim_ma_khong_can_dang_nhap() {
        createMovie(tokenAdmin, "Phim Dang Chieu", 120, List.of("Hanh dong"));
        db.update("UPDATE movies SET status = 'NOW_SHOWING'");
        createMovie(tokenAdmin, "Phim Sap Chieu", 95, List.of("Hai"));

        var response = client().get()
                .uri("/movies?status=NOW_SHOWING")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isEqualTo(1);
        assertThat(response.getBody().get(0).get("title").asText()).isEqualTo("Phim Dang Chieu");
    }

    @Test
    void thoi_luong_phim_khong_hop_le_bi_tu_choi_400() {
        var response = createMovie(tokenAdmin, "Phim Loi", 0, List.of("Hanh dong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<JsonNode> createMovie(String token, String title,
                                                 int durationMin, List<String> genres) {
        return client().post()
                .uri("/admin/movies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title", title,
                        "originalTitle", title,
                        "description", "Mo ta phim",
                        "durationMin", durationMin,
                        "genres", genres,
                        "ageRating", "T16",
                        "posterUrl", "https://example.com/poster.jpg",
                        "trailerUrl", "https://example.com/trailer.mp4",
                        "releaseDate", "2026-09-01"))
                .retrieve()
                .toEntity(JsonNode.class);
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
                .getBody()
                .get("accessToken").asText();
    }
}
