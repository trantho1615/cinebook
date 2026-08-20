package com.cinebook.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Metric nghiep vu cua duong giu ghe.
 *
 * CPU va heap thi Grafana nao cung co. Cai lam du an nay khac la nhung con so chi he thong
 * nay moi co: bao nhieu phan tram luot giu ghe dung phai nguoi nhanh tay hon, va duong nong
 * cua ca du an mat bao lau o p95.
 *
 * Mot lop mong boc MeterRegistry chu khong nhet registry thang vao use-case: ten metric nam
 * o mot cho, doi ten khong phai di sua nam noi.
 */
@Component
public class BookingMetrics {

    /** Luot giu ghe thanh cong. */
    public static final String THANH_CONG = "thanh_cong";
    /** Ghe da co nguoi khac giu — KHONG phai loi he thong. */
    public static final String XUNG_DOT = "xung_dot";
    /** Lua chon khong hop le, suat chieu khong dat duoc, v.v. */
    public static final String TU_CHOI = "tu_choi";

    private static final String TEN_TIMER = "cinebook.seat.hold";
    private static final String TEN_SWEEPER = "cinebook.sweeper.released";

    private final MeterRegistry registry;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Mot Timer duy nhat mang ca hai tin hieu: `_seconds_count` cho so luot, `_seconds_bucket`
     * cho phan vi. Dung them mot Counter rieng cung ten la tao ra hai nguon su that de lech.
     *
     * CARDINALITY: tag chi duoc nhan mot trong ba gia tri o tren. Khong bao gio dat seatId,
     * bookingId hay userId lam tag — moi gia tri tag la mot chuoi thoi gian rieng trong
     * Prometheus, gan id vao la cach nhanh nhat de giet instance Prometheus.
     */
    public void ghiNhanGiuGhe(String ketQua, Duration thoiGian) {
        Timer.builder(TEN_TIMER)
                .description("Thoi gian xu ly mot luot giu ghe")
                .tag("ket_qua", ketQua)
                .publishPercentileHistogram()
                .register(registry)
                .record(thoiGian);
    }

    public void ghiNhanSweeperNha(int soGhe) {
        if (soGhe <= 0) {
            return;
        }
        Counter.builder(TEN_SWEEPER)
                .description("So ghe het han da duoc sweeper nha")
                .register(registry)
                .increment(soGhe);
    }
}
