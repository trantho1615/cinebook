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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VenueTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";

    @Autowired
    JdbcTemplate db;

    private String tokenAdmin;
    private String tokenCustomer;

    @BeforeEach
    void reset() {
        truncate("seats", "rooms", "cinemas");
        truncate("users");

        register("khach@example.com");
        tokenCustomer = login("khach@example.com");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        tokenAdmin = login("admin@example.com");
    }

    @Test
    void tao_phong_thi_ghe_duoc_sinh_tu_dong_dung_so_luong() {
        String cinemaId = createCinema(tokenAdmin, "CGV Quan 1", "Quan 1").getBody().get("id").asText();

        var response = createRoom(tokenAdmin, cinemaId, "Phong 1", 8, 12);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("seatCount").asInt()).isEqualTo(96);

        Integer soGhe = db.queryForObject("SELECT count(*) FROM seats", Integer.class);
        assertThat(soGhe).isEqualTo(96);
    }

    @Test
    void hai_phong_cung_ten_trong_cung_rap_bi_tu_choi_409() {
        String cinemaId = createCinema(tokenAdmin, "CGV Quan 1", "Quan 1").getBody().get("id").asText();
        createRoom(tokenAdmin, cinemaId, "Phong 1", 5, 10);

        var response = createRoom(tokenAdmin, cinemaId, "Phong 1", 5, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(db.queryForObject("SELECT count(*) FROM rooms", Integer.class)).isEqualTo(1);
    }

    @Test
    void hai_rap_khac_nhau_duoc_dat_ten_phong_giong_nhau() {
        String rapA = createCinema(tokenAdmin, "CGV Quan 1", "Quan 1").getBody().get("id").asText();
        String rapB = createCinema(tokenAdmin, "CGV Quan 7", "Quan 7").getBody().get("id").asText();

        createRoom(tokenAdmin, rapA, "Phong 1", 5, 10);
        var response = createRoom(tokenAdmin, rapB, "Phong 1", 5, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void khach_khong_tao_duoc_rap() {
        var response = createCinema(tokenCustomer, "Rap Lau", "Quan 1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(db.queryForObject("SELECT count(*) FROM cinemas", Integer.class)).isZero();
    }

    private ResponseEntity<JsonNode> createCinema(String token, String name, String district) {
        return client().post()
                .uri("/admin/cinemas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "address", "123 Duong ABC",
                        "district", district, "city", "Ho Chi Minh"))
                .retrieve()
                .toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> createRoom(String token, String cinemaId, String name,
                                                int rowCount, int seatsPerRow) {
        return client().post()
                .uri("/admin/cinemas/" + cinemaId + "/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "roomType", "STANDARD",
                        "rowCount", rowCount, "seatsPerRow", seatsPerRow))
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
