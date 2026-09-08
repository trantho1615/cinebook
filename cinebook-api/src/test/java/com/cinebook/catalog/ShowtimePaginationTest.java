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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /showtimes phai co gioi han, va gioi han do phai la MAC DINH.
 *
 * Truy van nay khong he co LIMIT, khong phan trang, va nam trong nhom permitAll — bat ky ai
 * cung goi duoc ma khong can dang nhap. Voi du lieu demo (477 dong) no vo hinh. Milestone 10
 * nap 200 475 dong va do lai:
 *
 *   Parallel Seq Scan tren showtimes
 *   Sort Method: external merge  Disk: 4264kB (moi worker, ba worker)
 *   Execution Time: 229 ms
 *
 * cong voi viec tuan tu hoa 200 nghin ban ghi ra JSON. Khong mot test nao bat duoc no, vi
 * moi test deu chay tren du lieu nho.
 *
 * Day khong phai chuyen hieu nang. Mot endpoint cong khai ma khoi luong tra ve tang tuyen
 * tinh theo kich thuoc bang la mot duong de lam nga he thong.
 */
class ShowtimePaginationTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";
    private static final int MAC_DINH = 500;

    @Autowired
    JdbcTemplate db;

    @BeforeEach
    void reset() {
        truncate("showtimes");
        truncate("seats", "rooms", "cinemas");
        truncate("movie_genres", "movies");
        truncate("users");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        String token = login("admin@example.com");

        String movieId = createMovie(token);
        String roomId = createRoom(token, createCinema(token));

        // 600 suat chieu, chen thang bang SQL: goi API 600 lan lam test cham vo ich.
        //
        // Khe gio tuan tu cach nhau 3 gio la bat buoc — showtimes co
        // EXCLUDE USING gist (room_id WITH =, tstzrange(start_at, end_at) WITH &&)
        // nen rai gio ngau nhien la vi pham ngay.
        db.update("""
                INSERT INTO showtimes (id, movie_id, room_id, start_at, end_at,
                                       base_price, status, created_at)
                SELECT gen_random_uuid(), ?::uuid, ?::uuid,
                       now() + (i * interval '3 hours'),
                       now() + (i * interval '3 hours') + interval '2 hours',
                       90000, 'SCHEDULED', now()
                  FROM generate_series(1, 600) i
                """, movieId, roomId);
    }

    @Test
    void khong_truyen_gi_thi_bi_chan_o_gioi_han_mac_dinh() {
        assertThat(soDong("/showtimes"))
                .as("600 dong trong bang, nhung mot loi goi khong tham so khong duoc keo het ve")
                .isEqualTo(MAC_DINH);
    }

    @Test
    void limit_duoc_ton_trong() {
        assertThat(soDong("/showtimes?limit=10")).isEqualTo(10);
    }

    @Test
    void offset_bo_qua_dung_so_dong_va_khong_lap_lai_ket_qua() {
        JsonNode trang1 = goi("/showtimes?limit=5");
        JsonNode trang2 = goi("/showtimes?limit=5&offset=5");

        assertThat(trang1.size()).isEqualTo(5);
        assertThat(trang2.size()).isEqualTo(5);
        assertThat(id(trang2, 0))
                .as("trang thu hai phai bat dau o dong thu sau, khong lap lai trang mot")
                .isNotEqualTo(id(trang1, 0));
    }

    @Test
    void limit_vuot_tran_bi_tu_choi() {
        assertThat(ma("/showtimes?limit=5000")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ma("/showtimes?limit=0")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ma("/showtimes?offset=-1")).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Gioi han khong duoc lam hong duong ma UI that su goi.
     *
     * Trang chu goi /showtimes?from=<bay gio>. Neu gioi han mac dinh dat thap hon so suat
     * sap chieu thi danh sach bi cat cut trong im lang — nguoi dung khong thay suat chieu
     * ma khong co loi nao bao.
     */
    @Test
    void duong_UI_that_su_goi_van_tra_du_trong_gioi_han() {
        int soDong = soDong("/showtimes?from=" + java.time.Instant.now());
        assertThat(soDong).isGreaterThan(100).isLessThanOrEqualTo(MAC_DINH);
    }

    private int soDong(String uri) {
        return goi(uri).size();
    }

    private String id(JsonNode arr, int i) {
        return arr.get(i).get("showtimeId").asText();
    }

    private JsonNode goi(String uri) {
        return client().get().uri(uri).retrieve().toEntity(JsonNode.class).getBody();
    }

    private HttpStatus ma(String uri) {
        return (HttpStatus) client().get().uri(uri).retrieve().toBodilessEntity().getStatusCode();
    }

    private String createMovie(String token) {
        return client().post().uri("/admin/movies")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Phim Thu", "originalTitle", "Test Movie",
                        "description", "Mo ta", "durationMin", 120,
                        "genres", List.of("Hanh dong"), "ageRating", "T16",
                        "posterUrl", "https://example.com/p.jpg",
                        "trailerUrl", "https://example.com/t.mp4",
                        "releaseDate", "2026-08-01"))
                .retrieve().toEntity(JsonNode.class).getBody().get("id").asText();
    }

    private String createCinema(String token) {
        return client().post().uri("/admin/cinemas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Rap Thu", "address", "1 Duong Thu",
                        "district", "Quan 1", "city", "Ho Chi Minh"))
                .retrieve().toEntity(JsonNode.class).getBody().get("id").asText();
    }

    private String createRoom(String token, String cinemaId) {
        return client().post().uri("/admin/cinemas/" + cinemaId + "/rooms")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Phong 1", "roomType", "STANDARD",
                        "rowCount", 5, "seatsPerRow", 10))
                .retrieve().toEntity(JsonNode.class).getBody().get("id").asText();
    }

    private void register(String email) {
        client().post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD,
                        "fullName", "Nguoi Dung", "phone", "0900000000"))
                .retrieve().toBodilessEntity();
    }

    private String login(String email) {
        return client().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .retrieve().toEntity(JsonNode.class).getBody().get("accessToken").asText();
    }
}
