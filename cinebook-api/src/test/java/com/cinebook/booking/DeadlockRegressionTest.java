package com.cinebook.booking;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chan hoi quy cho quyet dinh "sap xep seat_id truoc khi insert".
 *
 * Hai nhom thread xin cung mot cap ghe nhung truyen vao theo thu tu nguoc nhau.
 * Neu ai do bo ORDER BY trong SQL_INSERT_HOLDS, PostgreSQL se bao deadlock
 * (SQLState 40P01) va test nay do.
 *
 * Da kiem chung: tam bo ORDER BY thi test nay that su do.
 */
class DeadlockRegressionTest extends AbstractApiTest {

    private static final int SO_CAP = 60;

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
    void hai_nhom_xin_cung_cap_ghe_theo_thu_tu_nguoc_nhau_khong_gay_deadlock() throws Exception {
        UUID showtimeId = UUID.fromString(fixture.showtimeId());
        UUID userId = UUID.fromString(fixture.userIdA());
        UUID f7 = UUID.fromString(fixture.seatIds("D7").getFirst());
        UUID f8 = UUID.fromString(fixture.seatIds("D8").getFirst());

        AtomicInteger soDeadlock = new AtomicInteger();
        CountDownLatch vachXuatPhat = new CountDownLatch(1);
        CountDownLatch vachDich = new CountDownLatch(SO_CAP * 2);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < SO_CAP; i++) {
                pool.submit(() -> chay(vachXuatPhat, vachDich, soDeadlock,
                        () -> holdSeats.hold(userId, showtimeId, List.of(f7, f8))));
                pool.submit(() -> chay(vachXuatPhat, vachDich, soDeadlock,
                        () -> holdSeats.hold(userId, showtimeId, List.of(f8, f7))));
            }
            vachXuatPhat.countDown();
            assertThat(vachDich.await(120, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(soDeadlock.get())
                .as("khong duoc co deadlock nao — SQL_INSERT_HOLDS phai con ORDER BY s.seat_id")
                .isZero();

        // Va ket qua cuoi cung van dung: dung mot nguoi giu duoc ca cap ghe do.
        Integer soHold = db.queryForObject("""
                SELECT count(*) FROM seat_hold
                 WHERE status = 'HELD' AND seat_id IN (?::uuid, ?::uuid)
                """, Integer.class, f7.toString(), f8.toString());
        assertThat(soHold).isEqualTo(2);
    }

    private Void chay(CountDownLatch xuatPhat, CountDownLatch dich,
                      AtomicInteger soDeadlock, Runnable viec) throws InterruptedException {
        xuatPhat.await();
        try {
            viec.run();
        } catch (Exception e) {
            if (laDeadlock(e)) {
                soDeadlock.incrementAndGet();
            }
        } finally {
            dich.countDown();
        }
        return null;
    }

    private boolean laDeadlock(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "40P01".equals(sql.getSQLState())) {
                return true;
            }
            if (String.valueOf(t.getMessage()).toLowerCase().contains("deadlock")) {
                return true;
            }
        }
        return false;
    }
}
