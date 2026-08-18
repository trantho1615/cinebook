package com.cinebook.booking;

import com.cinebook.booking.domain.SeatsUnavailableException;
import com.cinebook.booking.infra.HoldSeatsUseCase;
import com.cinebook.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test quan trong nhat cua ca du an.
 *
 * Dung CountDownLatch de moi thread cho nhau tai vach xuat phat roi cung lao vao
 * mot luc — neu chi tao thread roi chay ngay thi chung se noi duoi nhau va khong
 * tao ra tranh chap that.
 *
 * Dung virtual thread (Java 21+) chu khong phai pool co dinh: voi pool 64 thi chi
 * 64 thread thuc su chay cung luc, 136 thread con lai nam xep hang va "200 thread
 * dong thoi" chi la noi qua.
 */
class ConcurrentSeatHoldTest extends AbstractApiTest {

    private static final int SO_THREAD = 200;

    @Autowired
    JdbcTemplate db;

    @Autowired
    HoldSeatsUseCase holdSeats;

    private BookingFixture fixture;

    @BeforeEach
    void reset() {
        fixture = BookingFixture.freshSetup(this, db);
    }

    @Test
    void hai_tram_thread_cung_gianh_mot_ghe_thi_dung_mot_nguoi_thang() throws Exception {
        UUID showtimeId = UUID.fromString(fixture.showtimeId());
        UUID seatId = UUID.fromString(fixture.seatIds("E1").getFirst());
        UUID userId = UUID.fromString(fixture.userIdA());

        AtomicInteger thanhCong = new AtomicInteger();
        AtomicInteger biTuChoi = new AtomicInteger();
        AtomicInteger loiKhac = new AtomicInteger();

        chayDongThoi(SO_THREAD, () -> {
            try {
                holdSeats.hold(userId, showtimeId, List.of(seatId));
                thanhCong.incrementAndGet();
            } catch (SeatsUnavailableException e) {
                biTuChoi.incrementAndGet();
            } catch (Exception e) {
                loiKhac.incrementAndGet();
            }
        });

        assertThat(thanhCong.get()).isEqualTo(1);
        // Sieu quan trong: 199 thread thua phai thua vi BI TU CHOI DUNG CACH, khong
        // phai vi loi ket noi hay timeout. Neu chi khang dinh "thanhCong == 1" thi
        // mot he thong hong hoan toan (moi request deu no) van lam test nay xanh.
        assertThat(biTuChoi.get())
                .as("moi thread thua phai nhan SeatsUnavailableException")
                .isEqualTo(SO_THREAD - 1);
        assertThat(loiKhac.get()).isZero();

        // Kiem tra o tang du lieu, khong tin vao bo dem trong bo nho.
        Integer soHold = db.queryForObject(
                "SELECT count(*) FROM seat_hold WHERE seat_id = ?::uuid AND status = 'HELD'",
                Integer.class, seatId.toString());
        assertThat(soHold).isEqualTo(1);
    }

    @Test
    void bat_bien_khong_ghe_nao_bi_giu_hai_lan_van_dung_sau_khi_ban_tai() throws Exception {
        UUID showtimeId = UUID.fromString(fixture.showtimeId());
        UUID userId = UUID.fromString(fixture.userIdA());
        List<String> tatCaGhe = fixture.seatIds(
                "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10");

        chayDongThoi(SO_THREAD, () -> {
            int i = ThreadLocalRandom.current().nextInt(9);
            List<UUID> cap = List.of(
                    UUID.fromString(tatCaGhe.get(i)),
                    UUID.fromString(tatCaGhe.get(i + 1)));
            try {
                holdSeats.hold(userId, showtimeId, cap);
            } catch (Exception ignored) {
                // Bi tu choi la ket qua hop le trong test nay.
            }
        });

        // Day la truy van kiem tra bat bien o spec muc 8.3: phai tra ve 0 dong.
        Integer viPham = db.queryForObject("""
                SELECT count(*) FROM (
                    SELECT showtime_id, seat_id
                      FROM seat_hold
                     WHERE status IN ('HELD', 'BOOKED')
                     GROUP BY showtime_id, seat_id
                    HAVING count(*) > 1
                ) AS vi_pham
                """, Integer.class);
        assertThat(viPham).isZero();
    }

    @Test
    void moi_booking_thanh_cong_deu_co_du_so_ghe_khong_co_don_giu_mot_phan() throws Exception {
        UUID showtimeId = UUID.fromString(fixture.showtimeId());
        UUID userId = UUID.fromString(fixture.userIdA());
        List<String> tatCaGhe = fixture.seatIds("B1", "B2", "B3", "B4", "B5");

        chayDongThoi(50, () -> {
            int i = ThreadLocalRandom.current().nextInt(3);
            List<UUID> ba = List.of(
                    UUID.fromString(tatCaGhe.get(i)),
                    UUID.fromString(tatCaGhe.get(i + 1)),
                    UUID.fromString(tatCaGhe.get(i + 2)));
            try {
                holdSeats.hold(userId, showtimeId, ba);
            } catch (Exception ignored) {
            }
        });

        // Moi booking co giu ghe phai co dung 3 ghe. Neu co booking 1 hoac 2 ghe thi
        // tinh all-or-nothing da vo.
        Integer bookingLoi = db.queryForObject("""
                SELECT count(*) FROM (
                    SELECT b.id
                      FROM bookings b
                      JOIN seat_hold h ON h.booking_id = b.id AND h.status = 'HELD'
                     GROUP BY b.id
                    HAVING count(*) <> 3
                ) AS loi
                """, Integer.class);
        assertThat(bookingLoi).isZero();
    }

    private void chayDongThoi(int soThread, Runnable viec) throws Exception {
        CountDownLatch vachXuatPhat = new CountDownLatch(1);
        CountDownLatch vachDich = new CountDownLatch(soThread);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < soThread; i++) {
                pool.submit(() -> {
                    vachXuatPhat.await();   // cho tat ca cung san sang
                    try {
                        viec.run();
                    } finally {
                        vachDich.countDown();
                    }
                    return null;
                });
            }
            vachXuatPhat.countDown();       // ban phat sung
            assertThat(vachDich.await(120, TimeUnit.SECONDS))
                    .as("tat ca thread phai ket thuc trong 120 giay")
                    .isTrue();
        }
    }
}
