package com.cinebook.observability;

import com.cinebook.booking.BookingFixture;
import com.cinebook.booking.infra.SweepExpiredHoldsUseCase;
import com.cinebook.support.AbstractApiTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMetricsTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    MeterRegistry registry;

    @Autowired
    SweepExpiredHoldsUseCase sweeper;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void giu_ghe_thanh_cong_lam_counter_nhich() {
        double truoc = demLuot("thanh_cong");

        hold(fixture.tokenA(), fixture.seatIds("A1"));

        assertThat(demLuot("thanh_cong")).isEqualTo(truoc + 1);
    }

    /**
     * Xung dot phai duoc dem RIENG.
     *
     * Gop chung vao "loi" thi mat dung cai tin hieu dang gia nhat: trong mot buoi ban ve
     * dong, ti le xung dot cho biet nguoi dung dang phai tranh nhau den muc nao — do la chi
     * so TRAI NGHIEM, khong phai chi so may.
     */
    @Test
    void giu_trung_ghe_lam_counter_xung_dot_nhich() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        double truocXungDot = demLuot("xung_dot");
        double truocThanhCong = demLuot("thanh_cong");

        hold(fixture.tokenB(), fixture.seatIds("A1"));

        assertThat(demLuot("xung_dot")).isEqualTo(truocXungDot + 1);
        assertThat(demLuot("thanh_cong"))
                .as("luot that bai khong duoc tinh la thanh cong")
                .isEqualTo(truocThanhCong);
    }

    @Test
    void thoi_gian_giu_ghe_duoc_do() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));

        double tongThoiGian = registry.get("cinebook.seat.hold")
                .tag("ket_qua", "thanh_cong").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS);

        assertThat(tongThoiGian).isPositive();
    }

    @Test
    void sweeper_dem_so_ghe_da_nha() {
        hold(fixture.tokenA(), fixture.seatIds("A1", "A2"));
        db.update("UPDATE seat_hold SET expires_at = now() - interval '1 minute' WHERE status = 'HELD'");
        db.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE status = 'PENDING'");
        double truoc = demSweeper();

        sweeper.sweep();

        assertThat(demSweeper()).isEqualTo(truoc + 2);
    }

    private double demLuot(String ketQua) {
        try {
            return registry.get("cinebook.seat.hold").tag("ket_qua", ketQua).timer().count();
        } catch (MeterNotFoundException e) {
            // Chua co luot nao voi nhan nay: Micrometer chi tao chuoi khi co so lieu dau tien.
            return 0;
        }
    }

    private double demSweeper() {
        try {
            return registry.get("cinebook.sweeper.released").counter().count();
        } catch (MeterNotFoundException e) {
            return 0;
        }
    }

    private void hold(String token, List<String> seatIds) {
        client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", seatIds))
                .retrieve()
                .toEntity(JsonNode.class);
    }
}
