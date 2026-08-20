package com.cinebook.payment;

import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nua quan trong hon cua cong thanh toan gia lap.
 *
 * Endpoint /demo/** ky webhook bang bi mat cua server, tuc no xac nhan don ma khong ai
 * phai tra tien. No PHAI khong ton tai o moi truong that. Lop nay co y KHONG bat profile
 * "demo" — neu mot ngay nao do @Profile bi go ra, no do ngay.
 */
class DemoGatewayAbsentTest extends AbstractApiTest {

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

    /**
     * Khang dinh vao HAU QUA, khong vao ma trang thai.
     *
     * Hai ban nhap truoc deu khong phan biet duoc gi. Ban dau goi vo danh va chap nhan 401 —
     * nhung go @Profile ra thi nguoi la van nhan 401, test van xanh. Ban thu hai goi kem
     * token nhung dung paymentId ngau nhien — loi goi do nga vi du lieu khong ton tai chu
     * khong phai vi endpoint vang mat, nen "khong phai 2xx" van dung ca khi endpoint co that.
     *
     * Ban nay dung mot giao dich CO THAT cua chinh nguoi goi: neu endpoint ton tai thi don
     * se thanh CONFIRMED ma khong ai tra dong nao. Do moi la thu can chan.
     */
    @Test
    void nguoi_dung_hop_le_khong_the_tu_xac_nhan_don_cua_minh() {
        client().post()
                .uri("/demo/payments/" + paymentId + "/succeed")
                .header("Authorization", "Bearer " + fixture.tokenA())
                .retrieve()
                .toBodilessEntity();

        assertThat(trangThaiBooking())
                .as("khong co profile demo thi khong ai xac nhan duoc don ma khong tra tien")
                .isEqualTo("PENDING");
        assertThat(trangThaiPayment()).isEqualTo("INITIATED");
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
