package com.cinebook.shared.outbox;

/**
 * Noi event di ra khoi he thong. Milestone worker se cam Kafka vao day; o milestone
 * nay mot cai dat ghi log la du de chung minh co che outbox hoat dong.
 */
public interface EventPublisher {

    void publish(String eventType, String payloadJson);
}
