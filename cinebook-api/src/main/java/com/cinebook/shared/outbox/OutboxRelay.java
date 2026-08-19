package com.cinebook.shared.outbox;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Doc event chua gui va publish.
 *
 * FOR UPDATE SKIP LOCKED cho phep nhieu instance relay chay song song ma khong can
 * leader election: instance nay khoa dong nao thi instance kia bo qua dong do.
 *
 * DA KIEM CHUNG BANG THUC NGHIEM: relay 1 giu khoa 3 dong dau trong 4 giay, relay 2
 * chay xen vao nhan ngay 3 dong tiep theo, khong cho mot nhip nao. Vi vay OutboxRelayJob
 * ben worker CO Y khong mang @SchedulerLock.
 */
@Component
public class OutboxRelay {

    private static final String SQL_CLAIM = """
            SELECT id, aggregate_type, aggregate_id::text AS aggregate_id,
                   event_type, payload::text AS payload
              FROM outbox_events
             WHERE published_at IS NULL
             ORDER BY created_at, id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """;

    private static final String SQL_MARK_PUBLISHED = """
            UPDATE outbox_events
               SET published_at = now(), attempt_count = attempt_count + 1
             WHERE id = :id
            """;

    private static final String SQL_MARK_FAILED = """
            UPDATE outbox_events
               SET attempt_count = attempt_count + 1, last_error = :error
             WHERE id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final EventPublisher publisher;

    public OutboxRelay(NamedParameterJdbcTemplate jdbc, EventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    public int relayBatch(int limit) {
        List<OutboxMessage> messages = jdbc.query(SQL_CLAIM,
                new MapSqlParameterSource().addValue("limit", limit),
                (rs, n) -> new OutboxMessage(
                        rs.getLong("id"),
                        rs.getString("aggregate_type"),
                        UUID.fromString(rs.getString("aggregate_id")),
                        rs.getString("event_type"),
                        rs.getString("payload")));

        int daGui = 0;
        for (OutboxMessage message : messages) {
            try {
                publisher.publish(message);
                jdbc.update(SQL_MARK_PUBLISHED,
                        new MapSqlParameterSource().addValue("id", message.id()));
                daGui++;
            } catch (RuntimeException e) {
                // Khong danh dau da gui: lan relay sau se thu lai. attempt_count va
                // last_error de lai dau vet cho viec dieu tra.
                jdbc.update(SQL_MARK_FAILED, new MapSqlParameterSource()
                        .addValue("id", message.id())
                        .addValue("error", String.valueOf(e.getMessage())));
            }
        }
        return daGui;
    }
}
