package com.cinebook.common.event;

import java.time.Instant;

/**
 * Contract cua mot domain event truoc khi ghi vao bang outbox_events.
 * payload la chuoi JSON da serialize.
 */
public record DomainEvent(
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant occurredAt) {
}
