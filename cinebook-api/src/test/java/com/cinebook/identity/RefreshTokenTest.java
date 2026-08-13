package com.cinebook.identity;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest extends AbstractApiTest {

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
    void refresh_tra_ve_cap_token_moi_va_token_cu_khong_dung_lai_duoc() {
        String refresh1 = login().get("refreshToken").asText();

        var response = refresh(refresh1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refresh2 = response.getBody().get("refreshToken").asText();
        assertThat(refresh2).isNotBlank().isNotEqualTo(refresh1);
        assertThat(response.getBody().get("accessToken").asText()).isNotBlank();
    }

    @Test
    void dung_lai_refresh_token_cu_se_huy_toan_bo_family() {
        String refresh1 = login().get("refreshToken").asText();
        String refresh2 = refresh(refresh1).getBody().get("refreshToken").asText();

        // Ke tan cong dung lai token da bi xoay vong
        var lanThuHai = refresh(refresh1);
        assertThat(lanThuHai.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Va day moi la diem mau chot: token HOP LE cua nguoi dung that
        // cung bi vo hieu, vi ca chuoi token da bi coi la lo.
        var nguoiDungThat = refresh(refresh2);
        assertThat(nguoiDungThat.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_token_bia_dat_bi_tu_choi_401() {
        var response = refresh("token-hoan-toan-bia-dat-khong-ton-tai");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_lam_refresh_token_het_hieu_luc() {
        String refresh = login().get("refreshToken").asText();

        var logout = client().post()
                .uri("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refresh))
                .retrieve()
                .toBodilessEntity();
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(refresh(refresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void hai_lan_dang_nhap_tao_hai_family_doc_lap() {
        String refreshMayA = login().get("refreshToken").asText();
        String refreshMayB = login().get("refreshToken").asText();

        // Lam lo family cua may A
        refresh(refreshMayA);
        refresh(refreshMayA);

        // May B khong bi anh huong: dang nhap tren thiet bi khac phai song sot
        assertThat(refresh(refreshMayB).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode login() {
        return client().post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", EMAIL, "password", PASSWORD))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();
    }

    private ResponseEntity<JsonNode> refresh(String refreshToken) {
        return client().post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
