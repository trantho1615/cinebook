package com.cinebook.identity;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @BeforeEach
    void reset() {
        truncate("users");
    }

    @Test
    void dang_ky_thanh_cong_tra_201_va_khong_luu_mat_khau_dang_tho() {
        var response = client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "an@example.com",
                        "password", "MatKhauRatManh123",
                        "fullName", "Nguyen Van An",
                        "phone", "0901234567"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String hash = db.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'an@example.com'", String.class);
        assertThat(hash)
                .doesNotContain("MatKhauRatManh123")
                .startsWith("$2");   // dinh dang BCrypt

        String role = db.queryForObject(
                "SELECT role FROM users WHERE email = 'an@example.com'", String.class);
        assertThat(role).isEqualTo("CUSTOMER");
    }

    @Test
    void email_trung_bi_tu_choi_ke_ca_khi_go_khac_hoa_thuong() {
        register("binh@example.com", "MatKhauRatManh123");

        var response = client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "BINH@Example.COM",
                        "password", "MatKhauKhac456",
                        "fullName", "Tran Thi Binh",
                        "phone", "0907654321"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("EMAIL_ALREADY_USED");

        Integer count = db.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void email_sai_dinh_dang_bi_tu_choi_400() {
        var response = client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "khong-phai-email",
                        "password", "MatKhauRatManh123",
                        "fullName", "Le Van Cuong",
                        "phone", "0900000000"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mat_khau_ngan_hon_10_ky_tu_bi_tu_choi_400() {
        var response = client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "dung@example.com",
                        "password", "ngan",
                        "fullName", "Pham Van Dung",
                        "phone", "0900000001"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void register(String email, String password) {
        client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password,
                        "fullName", "Nguoi Dung",
                        "phone", "0900000000"))
                .retrieve()
                .toBodilessEntity();
    }
}
