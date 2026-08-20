package com.cinebook.worker.publish;

import com.cinebook.shared.outbox.EventPublisher;
import com.cinebook.shared.outbox.OutboxMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Dua event tu outbox len Kafka. Chi ton tai o cinebook-worker: cinebook-api khong bao
 * gio goi thang Kafka, no chi ghi vao outbox.
 */
@Component
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafka;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(OutboxMessage message) {
        String envelope = """
                {"eventId":%d,"eventType":"%s","aggregateType":"%s","aggregateId":"%s","payload":%s}"""
                .formatted(message.id(), message.eventType(), message.aggregateType(),
                        message.aggregateId(), message.payload());

        // Key la aggregateId: moi event cua cung mot don vao cung mot partition, nen thu tu
        // trong pham vi mot don duoc giu nguyen. Khong can thu tu toan cuc.
        //
        // join() de bien loi gui thanh exception dong bo: OutboxRelay dua vao exception de
        // biet ma KHONG danh dau published_at. Gui bat dong bo o day la tu tay dung lai
        // dung cai loi ma outbox sinh ra de tranh — bao "da gui" trong khi chua gui duoc.
        kafka.send(topicCho(message.aggregateType()), message.aggregateId().toString(), envelope)
                .join();
    }

    private String topicCho(String aggregateType) {
        return switch (aggregateType) {
            case "PAYMENT" -> "payment.events";
            default -> "booking.events";
        };
    }
}
