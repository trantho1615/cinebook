package com.cinebook.payment;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cong thanh toan gia lap cho demo — chi ton tai o profile "demo".
 *
 * DemoGatewayAbsentTest la nua con lai va la nua quan trong hon: mot cong gia ma moi
 * truong that cung goi duoc la lo hong xac nhan don ma khong tra tien.
 */
@ActiveProfiles("demo")
class DemoGatewayTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    private PaymentFixture fixture;
    private String bookingId;
    private String paymentId;

    @BeforeEach
    void reset() {
        fixture = PaymentFixture.freshSetup(this, db);
        bookingId = fixture.holdSeats(fixture.tokenA(), "A1");
        fixture.initiatePayment(this, fixture.tokenA(), bookingId);
        paymentId = db.queryForObject(
                "SELECT id::text FROM payments WHERE booking_id = ?::uuid", String.class, bookingId);
    }

    @Test
    void gia_lap_thanh_cong_thi_don_duoc_xac_nhan() {
        assertThat(goiCong("succeed")).isEqualTo(HttpStatus.OK);

        assertThat(trangThaiBooking()).isEqualTo("CONFIRMED");
        assertThat(trangThaiPayment()).isEqualTo("SUCCEEDED");
    }

    @Test
    void gia_lap_that_bai_thi_ghe_duoc_nha() {
        assertThat(goiCong("fail")).isEqualTo(HttpStatus.OK);

        assertThat(trangThaiBooking()).isEqualTo("CANCELLED");
        assertThat(trangThaiPayment()).isEqualTo("FAILED");

        Integer conGiu = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE status = 'HELD'", Integer.class);
        assertThat(conGiu).isZero();
    }

    /**
     * Cong gia di DUNG duong ma webhook that di, nen no thua huong luon tinh idempotent:
     * bam hai lan trong luc quay demo khong tao ra hai lan xu ly.
     */
    @Test
    void goi_hai_lan_chi_xu_ly_mot_lan() {
        goiCong("succeed");
        assertThat(goiCong("succeed")).isEqualTo(HttpStatus.OK);

        Integer soLanGhiNhan = db.queryForObject(
                "SELECT count(*) FROM payment_events", Integer.class);
        assertThat(soLanGhiNhan).isEqualTo(1);
    }

    private HttpStatus goiCong(String ketQua) {
        // Khong gan token: cong thanh toan that cung khong co token cua nguoi dung.
        return (HttpStatus) client().post()
                .uri("/demo/payments/" + paymentId + "/" + ketQua)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    private String trangThaiBooking() {
        return db.queryForObject("SELECT status FROM bookings WHERE id = ?::uuid",
                String.class, bookingId);
    }

    private String trangThaiPayment() {
        return db.queryForObject("SELECT status FROM payments WHERE id = ?::uuid",
                String.class, paymentId);
    }
}
