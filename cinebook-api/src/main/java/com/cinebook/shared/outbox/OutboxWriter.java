package com.cinebook.shared.outbox;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Ghi event vao outbox. PHAI duoc goi TRONG transaction nghiep vu — do la toan bo
 * ly do bang nay ton tai.
 */
@Component
public class OutboxWriter {

    private static final String SQL = """
            INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at)
            VALUES (:aggregateType, CAST(:aggregateId AS uuid), :eventType,
                    CAST(:payload AS jsonb), :createdAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(String aggregateType, UUID aggregateId, String eventType, String payloadJson) {
        jdbc.update(SQL, new MapSqlParameterSource()
                .addValue("aggregateType", aggregateType)
                .addValue("aggregateId", aggregateId.toString())
                .addValue("eventType", eventType)
                .addValue("payload", payloadJson)
                .addValue("createdAt", Timestamp.from(Instant.now())));
    }
}
