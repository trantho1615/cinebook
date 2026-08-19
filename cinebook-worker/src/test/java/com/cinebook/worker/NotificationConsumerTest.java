package com.cinebook.worker;

import com.cinebook.worker.support.AbstractWorkerTest;
import com.cinebook.worker.support.WorkerDataFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NotificationConsumerTest extends AbstractWorkerTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    KafkaTemplate<String, String> kafka;

    private UUID bookingId;

    @BeforeEach
    void reset() {
        WorkerDataFixture.donDep(db);
        bookingId = WorkerDataFixture.donDaXacNhan(db, "khach@example.com");
    }

    /**
     * Loi hua cot loi cua consumer: giao hang at-least-once, xu ly dung mot lan.
     *
     * Day khong phai truong hop hiem — Kafka gui lai bat cu khi nao consumer chua kip
     * commit offset, va relay cung co the gui lai neu no chet sau khi publish nhung truoc
     * khi kip UPDATE published_at.
     */
    @Test
    void cung_mot_event_den_hai_lan_chi_sinh_mot_thong_bao() {
        long eventId = 777L;

        kafka.send("booking.events", bookingId.toString(), envelope(eventId)).join();
        kafka.send("booking.events", bookingId.toString(), envelope(eventId)).join();

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(soThongBao()).isEqualTo(1));

        // Va giu nguyen o 1 sau khi cho them mot nhip — khong phai chi "chua kip xu ly cai
        // thu hai".
        await().pollDelay(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(soThongBao()).isEqualTo(1));
    }

    @Test
    void event_khong_phai_BookingConfirmed_thi_khong_gui_gi() {
        String khac = """
                {"eventId":888,"eventType":"BookingExpired","aggregateType":"BOOKING",\
                "aggregateId":"%s","payload":{}}""".formatted(bookingId);

        kafka.send("booking.events", bookingId.toString(), khac).join();

        await().pollDelay(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(soThongBao()).isZero());
    }

    private String envelope(long eventId) {
        return """
                {"eventId":%d,"eventType":"BookingConfirmed","aggregateType":"BOOKING",\
                "aggregateId":"%s","payload":{"bookingId":"%s"}}"""
                .formatted(eventId, bookingId, bookingId);
    }

    private Integer soThongBao() {
        return db.queryForObject("SELECT count(*) FROM notifications WHERE booking_id = ?::uuid",
                Integer.class, bookingId.toString());
    }
}
