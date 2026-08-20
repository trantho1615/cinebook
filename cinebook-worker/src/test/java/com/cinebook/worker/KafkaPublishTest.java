package com.cinebook.worker;

import com.cinebook.shared.outbox.EventPublisher;
import com.cinebook.shared.outbox.OutboxRelay;
import com.cinebook.worker.support.AbstractWorkerTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaPublishTest extends AbstractWorkerTest {

    @Autowired
    JdbcTemplate db;

    @Autowired
    NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    OutboxRelay relay;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @BeforeEach
    void reset() {
        db.execute("TRUNCATE TABLE outbox_events CASCADE");
    }

    @Test
    void event_trong_outbox_di_len_topic_booking_events() {
        UUID bookingId = UUID.randomUUID();
        ghiOutbox("BOOKING", bookingId, "BookingConfirmed",
                "{\"bookingId\":\"" + bookingId + "\"}");

        assertThat(relay.relayBatch(10)).isEqualTo(1);

        // Key la aggregateId: moi event cua cung mot don vao cung mot partition.
        String banTin = timBanTinTheoKey("booking.events", bookingId.toString());
        assertThat(banTin)
                .contains("BookingConfirmed")
                .contains(bookingId.toString())
                // eventId la thu consumer khu trung theo — thieu no la at-least-once
                // tro thanh mot loi hua khong giu duoc.
                .contains("\"eventId\"");
    }

    @Test
    void event_thanh_toan_di_len_topic_rieng() {
        UUID paymentId = UUID.randomUUID();
        ghiOutbox("PAYMENT", paymentId, "PaymentSucceeded", "{}");

        relay.relayBatch(10);

        assertThat(timBanTinTheoKey("payment.events", paymentId.toString()))
                .contains("PaymentSucceeded");
    }

    @Test
    void gui_xong_thi_danh_dau_published_at() {
        ghiOutbox("BOOKING", UUID.randomUUID(), "BookingConfirmed", "{}");

        relay.relayBatch(10);

        Integer conTreo = db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Integer.class);
        assertThat(conTreo).isZero();
    }

    /**
     * Spec muc 7: "Kafka chet -> event nam lai trong outbox, relay tu retry voi
     * attempt_count va last_error".
     *
     * Dung mot relay rieng cam vao publisher luon nem loi, thay vi tat container Kafka:
     * nhanh hon va khong lam anh huong cac test khac trong cung lan chay.
     */
    @Test
    void kafka_chet_thi_event_nam_lai_trong_outbox() {
        ghiOutbox("BOOKING", UUID.randomUUID(), "BookingConfirmed", "{}");

        EventPublisher hong = message -> {
            throw new IllegalStateException("broker khong tra loi");
        };
        new OutboxRelay(namedJdbc, hong).relayBatch(10);

        var row = db.queryForMap(
                "SELECT published_at, attempt_count, last_error FROM outbox_events");
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("attempt_count")).isEqualTo(1);
        assertThat(row.get("last_error")).asString().contains("broker khong tra loi");

        // Nhip relay sau van gui duoc: khong mat event nao.
        assertThat(relay.relayBatch(10)).isEqualTo(1);
    }

    private void ghiOutbox(String aggregateType, UUID id, String eventType, String payload) {
        db.update("""
                INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at)
                VALUES (?, ?::uuid, ?, ?::jsonb, now())
                """, aggregateType, id.toString(), eventType, payload);
    }

    /**
     * Doc TAT CA ban tin cua topic roi loc theo key, chu khong lay ban tin dau tien.
     *
     * TRUNCATE bang outbox giua cac test khong xoa gi trong Kafka: topic van giu nguyen
     * lich su, nen "ban tin dau tien" la cua test chay truoc do. Da gap dung tinh huong
     * nay: test doi key cua don minh vua tao nhung nhan duoc key cua test truoc.
     */
    private String timBanTinTheoKey(String topic, String key) {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", bootstrapServers);
        props.setProperty("group.id", "test-" + UUID.randomUUID());
        props.setProperty("auto.offset.reset", "earliest");

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));

            List<String> khopKey = new ArrayList<>();
            for (int lan = 0; lan < 5 && khopKey.isEmpty(); lan++) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, String> record : records) {
                    if (key.equals(record.key())) {
                        khopKey.add(record.value());
                    }
                }
            }

            assertThat(khopKey).as("khong thay ban tin nao co key %s tren %s", key, topic)
                    .hasSize(1);
            return khopKey.getFirst();
        }
    }
}
