package com.cinebook.catalog;

import com.cinebook.support.AbstractApiTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hai bang tra cuu TINH khong duoc doc lai o moi request.
 *
 * Do o Milestone 10: mot lan goi GET /showtimes/{id}/seats chay BON truy van, moi truy van
 * muon mot ket noi rieng vi duong doc khong co @Transactional. Thoi gian thuc thi SQL that
 * cua ca bon cong lai la 0,104 ms, nhung qua JDBC chung ton 2,762 ms — mot cau `SELECT 1`
 * khong lam gi ca cung ton 0,66 ms. Nut co chai la SO VONG MANG, khong phai truy van.
 *
 * Hai trong bon truy van doc du lieu khong bao gio doi:
 *
 *   price_rules  3 dong, khong co duong ghi nao trong ung dung. Flyway V6 nap roi thoi, va
 *                CHECK constraint chi cho phep dung ba gia tri STANDARD/VIP/COUPLE.
 *
 *   seats        Chi duoc tao mot lan cung luc tao phong (POST /admin/cinemas/{id}/rooms).
 *                Khong co endpoint nao sua hay xoa ghe, va is_active khong bao gio duoc ghi.
 *                Phong moi co uuid moi nen khong bao gio dung vao dong da cache.
 *
 * Test nay do bang counter cinebook.reference.load. Khong co cache thi 20 lan goi sinh ra
 * 20 lan doc moi bang; co cache thi nhieu nhat 1.
 *
 * So sanh bang HIEU chu khong bang gia tri tuyet doi: cache song theo application context,
 * nen cac test chay truoc co the da lam nong no roi.
 */
class ReferenceDataCacheTest extends AbstractApiTest {

    private static final String PASSWORD = "MatKhauRatManh123";
    private static final int SO_LAN_GOI = 20;

    @Autowired
    JdbcTemplate db;

    @Autowired
    MeterRegistry registry;

    private String showtimeId;

    @BeforeEach
    void reset() {
        truncate("showtimes");
        truncate("seats", "rooms", "cinemas");
        truncate("movie_genres", "movies");
        truncate("users");

        register("admin@example.com");
        db.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        String tokenAdmin = login("admin@example.com");

        String movieId = createMovie(tokenAdmin);
        String cinemaId = createCinema(tokenAdmin);
        String roomId = createRoom(tokenAdmin, cinemaId);
        showtimeId = createShowtime(tokenAdmin, movieId, roomId);
    }

    @Test
    void bang_gia_chi_duoc_doc_mot_lan_du_goi_bao_nhieu_request() {
        double truoc = soLanDoc("price_rules");

        goiSoDoGhe(SO_LAN_GOI);

        // Nguong 2 chu khong phai 1: cache co TTL, va 20 request co the vat qua mot moc
        // het han. Khong co cache thi con so nay la 20, nen phep do van phan biet ro.
        assertThat(soLanDoc("price_rules") - truoc)
                .as("price_rules doi rat it, %d request khong duoc sinh ra %d luot doc",
                        SO_LAN_GOI, SO_LAN_GOI)
                .isLessThanOrEqualTo(2);
    }

    @Test
    void so_do_ghe_cua_mot_phong_chi_duoc_doc_mot_lan() {
        double truoc = soLanDoc("seats");

        goiSoDoGhe(SO_LAN_GOI);

        assertThat(soLanDoc("seats") - truoc)
                .as("so do ghe cua mot phong khong doi, %d request khong duoc sinh ra %d luot doc",
                        SO_LAN_GOI, SO_LAN_GOI)
                .isLessThanOrEqualTo(1);
    }

    /**
     * Nua thu hai, va la nua dang lo: cache khong duoc lam sai ket qua.
     *
     * Gia moi ghe = showtimes.base_price + price_rules.surcharge, tuc no phu thuoc suat
     * chieu chu khong chi phu thuoc phong. Cache so do ghe KEM GIA se tra gia cua suat
     * chieu dau tien cho moi suat chieu sau do — mot loi tinh tien im lang. Vi vay chi
     * cache phan tinh (id, hang, so ghe, loai ghe), con gia thi tinh lai theo tung suat.
     */
    @Test
    void cache_khong_lam_sai_gia_khi_hai_suat_chieu_khac_base_price() {
        String tokenAdmin = login("admin@example.com");
        String movieId = db.queryForObject("SELECT id::text FROM movies LIMIT 1", String.class);
        String roomId = db.queryForObject("SELECT id::text FROM rooms LIMIT 1", String.class);

        // Suat thu hai, CUNG phong nhung base_price gap doi.
        String suatDat = createShowtime(tokenAdmin, movieId, roomId,
                Instant.parse("2026-09-02T12:00:00Z"), 180000L);

        long giaRe = giaGheDauTien(showtimeId);
        long giaDat = giaGheDauTien(suatDat);

        assertThat(giaRe).isEqualTo(90000L);
        assertThat(giaDat)
                .as("suat chieu dat hon phai ra gia dat hon — cache khong duoc giu lai gia cu")
                .isEqualTo(180000L);
    }

    private long giaGheDauTien(String id) {
        JsonNode body = client().get().uri("/showtimes/" + id + "/seats")
                .retrieve().toEntity(JsonNode.class).getBody();
        return body.get(0).get("price").asLong();
    }

    private void goiSoDoGhe(int soLan) {
        for (int i = 0; i < soLan; i++) {
            var res = client().get().uri("/showtimes/" + showtimeId + "/seats")
                    .retrieve().toBodilessEntity();
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    /**
     * KHONG bat MeterNotFoundException o day.
     *
     * Ban dau ham nay tra ve 0 khi chua tim thay counter, va nhu vay ca hai test deu DAU
     * khi chua co cache — hieu cua hai so 0 luon bang 0. Mot test khong the do duoc thi
     * phai gay, chu khong duoc im lang bao xanh.
     *
     * Counter duoc dang ky ngay trong constructor cua bean, khong doi lan nap dau tien.
     */
    private double soLanDoc(String bang) {
        return registry.get("cinebook.reference.load").tag("bang", bang).counter().count();
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

    private String createShowtime(String token, String movieId, String roomId) {
        return createShowtime(token, movieId, roomId,
                Instant.parse("2026-09-01T12:00:00Z"), 90000L);
    }

    private String createShowtime(String token, String movieId, String roomId,
                                  Instant startAt, long basePrice) {
        return client().post().uri("/admin/showtimes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("movieId", movieId, "roomId", roomId,
                        "startAt", startAt.toString(), "basePrice", basePrice))
                .retrieve().toEntity(JsonNode.class).getBody().get("id").asText();
    }
}
