package com.cinebook.identity;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoginTest extends AbstractApiTest {

    private static final String EMAIL = "an@example.com";
    private static final String PASSWORD = "MatKhauRatManh123";

    @BeforeEach
    void reset() {
        truncate("users");
        client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL, "password", PASSWORD,
                        "fullName", "Nguyen Van An", "phone", "0901234567"))
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void dang_nhap_dung_tra_ve_access_token() {
        var response = login(EMAIL, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(response.getBody().get("expiresInSeconds").asLong()).isEqualTo(900);
    }

    @Test
    void sai_mat_khau_va_email_khong_ton_tai_tra_ve_cung_mot_loi() {
        var saiMatKhau = login(EMAIL, "MatKhauSaiHoanToan");
        var khongTonTai = login("khongcoai@example.com", PASSWORD);

        assertThat(saiMatKhau.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(khongTonTai.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Hai truong hop phai khong phan biet duoc, neu khong ke tan cong do duoc
        // email nao da dang ky.
        assertThat(saiMatKhau.getBody().get("code").asText())
                .isEqualTo(khongTonTai.getBody().get("code").asText())
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(saiMatKhau.getBody().get("message").asText())
                .isEqualTo(khongTonTai.getBody().get("message").asText());
    }

    @Test
    void goi_endpoint_can_dang_nhap_ma_khong_co_token_tra_401() {
        var response = client().get().uri("/auth/me").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void goi_endpoint_can_dang_nhap_voi_token_hop_le_tra_dung_user() {
        String token = login(EMAIL, PASSWORD).getBody().get("accessToken").asText();

        var response = client().get()
                .uri("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo(EMAIL);
        assertThat(response.getBody().get("role").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void token_bi_sua_chu_ky_bi_tu_choi_401() {
        String token = login(EMAIL, PASSWORD).getBody().get("accessToken").asText();
        // Doi mot ky tu trong phan chu ky -> chu ky khong con khop
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        var response = client().get()
                .uri("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return client().post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
