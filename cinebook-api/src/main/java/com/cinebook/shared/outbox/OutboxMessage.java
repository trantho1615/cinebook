package com.cinebook.shared.outbox;

import java.util.UUID;

/**
 * Mot event roi khoi outbox.
 *
 * id chinh la outbox_events.id — thu ma consumer khu trung theo. Giao hang la
 * at-least-once nen moi consumer BUOC phai idempotent, va no can mot id on dinh do ben
 * gui sinh ra chu khong phai tu doan tu noi dung.
 */
public record OutboxMessage(
        long id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload) {
}
