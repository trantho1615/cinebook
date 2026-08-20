package com.cinebook.booking;

import com.cinebook.booking.infra.SweepExpiredHoldsUseCase;
import com.cinebook.support.AbstractApiTest;
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

class SweeperTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    SweepExpiredHoldsUseCase sweeper;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void ghe_het_han_duoc_nha_voi_ly_do_SWEPT() {
        hold(fixture.tokenA(), fixture.seatIds("A1", "A2"));
        lamChoHetHan();

        assertThat(sweeper.sweep()).isEqualTo(2);

        Integer conGiu = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class);
        assertThat(conGiu).isZero();

        Integer daQuet = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE release_reason = 'SWEPT'", Integer.class);
        assertThat(daQuet).isEqualTo(2);
    }

    @Test
    void don_qua_han_chuyen_sang_EXPIRED() {
        String bookingId = hold(fixture.tokenA(), fixture.seatIds("A1"));
        lamChoHetHan();

        sweeper.sweep();

        String trangThai = db.queryForObject(
                "SELECT status FROM bookings WHERE id = ?::uuid", String.class, bookingId);
        assertThat(trangThai).isEqualTo("EXPIRED");
    }

    @Test
    void ghe_con_han_khong_bi_dong_toi() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));

        assertThat(sweeper.sweep()).isZero();

        Integer conGiu = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class);
        assertThat(conGiu).isEqualTo(1);
    }

    @Test
    void quet_hai_lan_khong_lam_gi_them() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        lamChoHetHan();

        sweeper.sweep();
        assertThat(sweeper.sweep()).isZero();

        Integer soEvent = db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'BookingExpired'",
                Integer.class);
        assertThat(soEvent).isEqualTo(1);
    }

    /**
     * Bang chung cho cau khang dinh o spec muc 6.6: tinh dung dan KHONG phu thuoc sweeper.
     *
     * Khong goi sweep() lan nao, nguoi thu hai van giu duoc ghe da het han cua nguoi thu
     * nhat — nho lazy expiration ngay trong transaction giu ghe. Sweeper chi mua tinh kip
     * thoi, va test nay la thu giu cho su phan vai do khong bi lu mo di.
     */
    @Test
    void khong_co_sweeper_thi_van_giu_duoc_ghe_da_het_han_cua_nguoi_khac() {
        hold(fixture.tokenA(), fixture.seatIds("A1"));
        lamChoHetHan();

        String bookingCuaB = hold(fixture.tokenB(), fixture.seatIds("A1"));

        assertThat(bookingCuaB).isNotBlank();
        Integer gheCuaB = db.queryForObject("""
                SELECT count(*) FROM seat_hold WHERE booking_id = ?::uuid AND status = 'HELD'
                """, Integer.class, bookingCuaB);
        assertThat(gheCuaB).isEqualTo(1);
    }

    /**
     * Day gio he thong ve qua khu thay vi cho 10 phut. Test khong duoc cho doi.
     */
    private void lamChoHetHan() {
        db.update("UPDATE seat_hold SET expires_at = now() - interval '1 minute' WHERE status = 'HELD'");
        db.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE status = 'PENDING'");
    }

    private String hold(String token, List<String> seatIds) {
        JsonNode body = client().post()
                .uri("/showtimes/" + fixture.showtimeId() + "/holds")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("seatIds", seatIds))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody();
        return body.get("bookingId").asText();
    }
}
