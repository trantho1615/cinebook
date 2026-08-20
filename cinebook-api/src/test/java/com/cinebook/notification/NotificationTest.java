package com.cinebook.notification;

import com.cinebook.booking.BookingFixture;
import com.cinebook.notification.infra.SendBookingConfirmedUseCase;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    SendBookingConfirmedUseCase useCase;

    private BookingFixture fixture;
    private UUID bookingId;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
        bookingId = UUID.fromString(hold(fixture.tokenA(), fixture.seatIds("A1", "A2")));
    }

    /**
     * Test quan trong nhat cua task nay. Kafka giao hang at-least-once, nen cung mot event
     * den hai lan la chuyen binh thuong chu khong phai truong hop hiem.
     */
    @Test
    void cung_mot_event_giao_hai_lan_chi_gui_mot_lan() {
        assertThat(useCase.handle(42L, bookingId)).isTrue();
        assertThat(useCase.handle(42L, bookingId)).isFalse();

        Integer soBanGhi = db.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = 42", Integer.class);
        assertThat(soBanGhi).isEqualTo(1);
    }

    @Test
    void gui_dung_email_cua_chu_don() {
        useCase.handle(1L, bookingId);

        String nguoiNhan = db.queryForObject(
                "SELECT recipient FROM notifications WHERE event_id = 1", String.class);
        assertThat(nguoiNhan).isEqualTo("a@example.com");
    }

    @Test
    void hai_event_khac_nhau_cua_cung_mot_don_deu_duoc_gui() {
        assertThat(useCase.handle(1L, bookingId)).isTrue();
        assertThat(useCase.handle(2L, bookingId)).isTrue();

        Integer soBanGhi = db.queryForObject(
                "SELECT count(*) FROM notifications WHERE booking_id = ?::uuid",
                Integer.class, bookingId.toString());
        assertThat(soBanGhi).isEqualTo(2);
    }

    /**
     * Consumer khong duoc no khi don da bien mat — event van co the den sau khi du lieu
     * bi don di. Tra false va di tiep, khong nem exception khien Kafka retry vo han.
     */
    @Test
    void don_khong_ton_tai_thi_bo_qua_chu_khong_no() {
        assertThat(useCase.handle(9L, UUID.randomUUID())).isFalse();

        Integer soBanGhi = db.queryForObject("SELECT count(*) FROM notifications", Integer.class);
        assertThat(soBanGhi).isZero();
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
