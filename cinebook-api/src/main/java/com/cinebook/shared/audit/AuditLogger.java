package com.cinebook.shared.audit;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Nam o shared chu khong o booking: bang audit_log hoan toan tong quat
 * (aggregate_type, aggregate_id, action, actor_type), va payment cung phai ghi vao no
 * khi hoan tien. Cung ly do voi shared/outbox.
 *
 * Bang duoc tao trong V7__booking.sql vi day la noi no ra doi; migration chi them chu
 * khong sua, nen file khong doi ten theo.
 */
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
