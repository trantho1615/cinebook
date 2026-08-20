package com.cinebook.shared.outbox;

/**
 * Noi event di ra khoi he thong. cinebook-worker cam KafkaEventPublisher vao day;
 * LoggingEventPublisher duoc giu lai de test cua cinebook-api khong can Kafka.
 */
public interface EventPublisher {

    void publish(OutboxMessage message);
}
