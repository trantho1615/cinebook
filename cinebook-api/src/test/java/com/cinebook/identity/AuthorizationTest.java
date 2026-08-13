package com.cinebook.identity;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";

    @Autowired
    JdbcTemplate db;

    private String tokenA;
    private String idA;
    private String tokenB;
    private String idB;
    private String tokenAdmin;

    @BeforeEach
    void reset() {
        truncate("users");

        idA = register("a@example.com");
        tokenA = login("a@example.com");

        idB = register("b@example.com");
        tokenB = login("b@example.com");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        tokenAdmin = login("admin@example.com");
    }

    @Test
    void user_khong_xem_duoc_thong_tin_cua_user_khac() {
        var response = client().get()
                .uri("/users/" + idB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).doesNotContain("b@example.com");
    }

    @Test
    void user_xem_duoc_thong_tin_cua_chinh_minh() {
        var response = client().get()
                .uri("/users/" + idA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo("a@example.com");
    }

    @Test
    void admin_xem_duoc_thong_tin_cua_bat_ky_user_nao() {
        var response = client().get()
                .uri("/users/" + idB)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo("b@example.com");
    }

    @Test
    void customer_khong_goi_duoc_endpoint_danh_cho_admin() {
        var response = client().get()
                .uri("/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_goi_duoc_endpoint_danh_cho_admin() {
        var response = client().get()
                .uri("/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isEqualTo(3);
    }

    @Test
    void user_khong_ton_tai_tra_404_chu_khong_phai_403() {
        String idKhongTonTai = "00000000-0000-0000-0000-000000000000";

        var response = client().get()
                .uri("/users/" + idKhongTonTai)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String register(String email) {
        return client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD,
                        "fullName", "Nguoi Dung", "phone", "0900000000"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody()
                .get("id").asText();
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
