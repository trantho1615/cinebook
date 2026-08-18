package com.cinebook.booking.infra;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class AuditLogger {

    public enum ActorType {
        USER,
        AGENT,
        SYSTEM
    }

    private static final String SQL = """
            INSERT INTO audit_log
                (aggregate_type, aggregate_id, action, actor_type, actor_id, payload, created_at)
            VALUES
                (:aggregateType, CAST(:aggregateId AS uuid), :action, :actorType,
                 CAST(:actorId AS uuid), CAST(:payload AS jsonb), :createdAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AuditLogger(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String aggregateType, UUID aggregateId, String action,
                       ActorType actorType, UUID actorId, String payloadJson) {
        jdbc.update(SQL, new MapSqlParameterSource()
                .addValue("aggregateType", aggregateType)
                .addValue("aggregateId", aggregateId.toString())
                .addValue("action", action)
                .addValue("actorType", actorType.name())
                .addValue("actorId", actorId == null ? null : actorId.toString())
                .addValue("payload", payloadJson)
                .addValue("createdAt", Timestamp.from(Instant.now())));
    }
}
