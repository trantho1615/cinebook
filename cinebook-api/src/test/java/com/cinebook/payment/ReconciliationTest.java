package com.cinebook.payment;

import com.cinebook.payment.infra.ReconcilePaymentsUseCase;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationTest extends AbstractApiTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    ReconcilePaymentsUseCase reconcile;

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
    void giao_dich_moi_tao_chua_den_han_thi_chua_doi_soat() {
        assertThat(reconcile.reconcile()).isZero();
        assertThat(trangThaiPayment()).isEqualTo("INITIATED");
    }

    @Test
    void giao_dich_treo_qua_lau_thi_chu_dong_hoi_cong_thanh_toan() {
        lamChoQuaHanDoiSoat();

        int soDoiSoat = reconcile.reconcile();

        assertThat(soDoiSoat).isEqualTo(1);
        // MockPaymentGateway tra ve SUCCEEDED cho moi txn bat dau bang MOCK-
        assertThat(trangThaiPayment()).isEqualTo("SUCCEEDED");
        assertThat(trangThaiBooking()).isEqualTo("CONFIRMED");
    }

    @Test
    void doi_soat_hai_lan_khong_lam_doi_gi_them() {
        lamChoQuaHanDoiSoat();

        reconcile.reconcile();
        int lanHai = reconcile.reconcile();

        assertThat(lanHai).isZero();
        assertThat(trangThaiBooking()).isEqualTo("CONFIRMED");
    }

    /**
     * Day thoi diem tao giao dich ve qua khu de vuot nguong doi soat.
     */
    private void lamChoQuaHanDoiSoat() {
        db.update("UPDATE payments SET created_at = now() - interval '30 minutes'");
    }

    private String trangThaiPayment() {
        return db.queryForObject("SELECT status FROM payments WHERE id = ?::uuid",
                String.class, paymentId);
    }

    private String trangThaiBooking() {
        return db.queryForObject("SELECT status FROM bookings WHERE id = ?::uuid",
                String.class, bookingId);
    }
}
