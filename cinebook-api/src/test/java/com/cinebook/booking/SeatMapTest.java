package com.cinebook.booking;

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

class SeatMapTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void ghe_chua_ai_giu_deu_o_trang_thai_available() {
        var seats = seatMap();

        assertThat(seats.size()).isEqualTo(50);
        for (JsonNode seat : seats) {
            assertThat(seat.get("status").asText()).isEqualTo("AVAILABLE");
        }
    }

    @Test
    void ghe_dang_bi_giu_hien_trang_thai_held_cho_moi_nguoi_cung_thay() {
        hold(fixture.tokenA(), fixture.seatIds("A1", "A2"));

        var seats = seatMap();

        assertThat(trangThaiCua(seats, "A1")).isEqualTo("HELD");
        assertThat(trangThaiCua(seats, "A2")).isEqualTo("HELD");
        assertThat(trangThaiCua(seats, "A3")).isEqualTo("AVAILABLE");
    }

    @Test
    void seat_map_xem_duoc_ma_khong_can_dang_nhap() {
        var response = client().get()
                .uri("/showtimes/" + fixture.showtimeId() + "/seats")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void goi_y_tra_ve_dung_so_ghe_lien_nhau_va_deu_con_trong() {
        var response = client().get()
                .uri("/showtimes/" + fixture.showtimeId() + "/seats/suggest?count=3")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode goiY = response.getBody();
        assertThat(goiY.size()).isEqualTo(3);

        String hang = goiY.get(0).get("rowLabel").asText();
        for (int i = 0; i < 3; i++) {
            assertThat(goiY.get(i).get("rowLabel").asText()).isEqualTo(hang);
            assertThat(goiY.get(i).get("status").asText()).isEqualTo("AVAILABLE");
        }
        assertThat(goiY.get(1).get("seatNumber").asInt())
                .isEqualTo(goiY.get(0).get("seatNumber").asInt() + 1);
    }

    @Test
    void goi_y_khong_bao_gio_tra_ve_ghe_da_bi_giu() {
        // Giu gan het hang C (hang giua, duoc uu tien)
        hold(fixture.tokenA(), fixture.seatIds("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8"));

        var goiY = client().get()
                .uri("/showtimes/" + fixture.showtimeId() + "/seats/suggest?count=2")
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();

        assertThat(goiY.size()).isEqualTo(2);
        for (JsonNode seat : goiY) {
            assertThat(seat.get("status").asText()).isEqualTo("AVAILABLE");
            assertThat(seat.get("label").asText())
                    .isNotIn("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8");
        }
    }

    @Test
    void het_ghe_lien_nhau_thi_tra_ve_404() {
        // Giu het 5 hang x 10 ghe = 50 ghe, moi lan toi da 8 ghe
        String[][] nhom = {
                {"A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8"},
                {"A9", "A10", "B1", "B2", "B3", "B4", "B5", "B6"},
                {"B7", "B8", "B9", "B10", "C1", "C2", "C3", "C4"},
                {"C5", "C6", "C7", "C8", "C9", "C10", "D1", "D2"},
                {"D3", "D4", "D5", "D6", "D7", "D8", "D9", "D10"},
                {"E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8"},
                {"E9", "E10"}
        };
        for (String[] g : nhom) {
            hold(fixture.tokenA(), fixture.seatIds(g));
        }

        var response = client().get()
                .uri("/showtimes/" + fixture.showtimeId() + "/seats/suggest?count=2")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode seatMap() {
        return client().get()
                .uri("/showtimes/" + fixture.showtimeId() + "/seats")
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();
    }

    private String trangThaiCua(JsonNode seats, String label) {
        for (JsonNode seat : seats) {
            if (seat.get("label").asText().equals(label)) {
                return seat.get("status").asText();
            }
        }
        throw new AssertionError("Khong tim thay ghe " + label);
    }

    private void hold(String token, List<String> seatIds) {
        client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", seatIds))
                .retrieve()
                .toBodilessEntity();
    }
}
