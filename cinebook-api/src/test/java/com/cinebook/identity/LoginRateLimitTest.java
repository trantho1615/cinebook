package com.cinebook.identity;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitTest extends AbstractApiTest {

    // Email rieng, khong dung chung voi LoginTest hay RefreshTokenTest. Test nay co y
    // khoa tai khoan, ma bo dem nam trong Redis nen truncate("users") khong xoa duoc no.
    // Dung chung email thi class nao chay sau se nhan 429 thay vi 200.
    private static final String EMAIL = "ratelimit@example.com";
    private static final String PASSWORD = "MatKhauRatManh123";
    private static final String SAI = "MatKhauSaiHoanToan";

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        truncate("users");
        // Bo dem nam trong Redis, khong bi truncate bang xoa. Phai don rieng,
        // neu khong test truoc se lam do test sau.
        var keys = redis.keys("login:fail:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        register(EMAIL);
    }

    @Test
    void sai_qua_5_lan_thi_bi_chan_429_ke_ca_khi_mat_khau_dung() {
        for (int i = 0; i < 5; i++) {
            assertThat(login(EMAIL, SAI).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // Lan thu 6 dung MAT KHAU DUNG: van phai bi chan, chung minh viec chan
        // xay ra TRUOC khi kiem tra mat khau.
        var response = login(EMAIL, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().get("code").asText()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");

        // Client can biet cho bao lau moi thu lai duoc, khong phai doan mo.
        String retryAfter = response.getHeaders().getFirst("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 900L);
    }

    @Test
    void dang_nhap_thanh_cong_xoa_bo_dem() {
        for (int i = 0; i < 4; i++) {
            login(EMAIL, SAI);
        }

        assertThat(login(EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Sau khi dang nhap thanh cong, quota phai duoc cap lai tu dau
        for (int i = 0; i < 5; i++) {
            assertThat(login(EMAIL, SAI).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void bo_dem_tach_rieng_theo_email() {
        register("ratelimit2@example.com");

        for (int i = 0; i < 6; i++) {
            login(EMAIL, SAI);
        }

        // Tai khoan khac khong bi va lay: neu dung bo dem chung theo IP thi
        // mot ke tan cong co the khoa toan bo nguoi dung sau cung NAT.
        assertThat(login("ratelimit2@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private ResponseEntity<JsonNode> login(String email, String password) {
        return client().post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
