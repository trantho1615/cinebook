package com.cinebook.shared.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * So event dang nam cho trong outbox.
 *
 * Con so nay phinh len la dau hieu Kafka hoac relay co van de — thay duoc TRUOC khi nguoi
 * dung kip phan nan vi khong nhan duoc email. Do la khac biet giua mot dashboard de nhin va
 * mot dashboard dung duoc.
 *
 * KHONG mang @Component: chi cinebook-worker dang ky bean nay (qua WorkerBeans). De api cung
 * bao thi hai deployable cung ghi mot con so tu cung mot bang, va Prometheus co hai chuoi
 * trung noi dung.
 */
public class OutboxMetrics {

    private static final String SQL_DEM = """
            SELECT count(*) FROM outbox_events WHERE published_at IS NULL
            """;

    /**
     * Prometheus scrape moi 5 giay, va gauge duoc doc moi lan scrape. Nho lai ket qua trong
     * mot khoang ngan de khong bien viec giam sat thanh mot nguon tai cho database.
     */
    private static final Duration THOI_GIAN_NHO = Duration.ofSeconds(3);

    private final JdbcTemplate jdbc;
    private final AtomicLong giaTriGanNhat = new AtomicLong();
    private volatile Instant docLuc = Instant.EPOCH;

    public OutboxMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;

        Gauge.builder("cinebook.outbox.pending", this, OutboxMetrics::demChuaGui)
                .description("So event trong outbox chua duoc publish")
                .register(registry);
    }

    private double demChuaGui() {
        if (Duration.between(docLuc, Instant.now()).compareTo(THOI_GIAN_NHO) > 0) {
            Long soDong = jdbc.queryForObject(SQL_DEM, Long.class);
            giaTriGanNhat.set(soDong == null ? 0 : soDong);
            docLuc = Instant.now();
        }
        return giaTriGanNhat.get();
    }
}
