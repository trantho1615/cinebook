package com.cinebook.booking.infra;

import com.cinebook.booking.domain.DuplicateRequestException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bao dam mot yeu cau chi duoc thuc hien mot lan du client gui lai bao nhieu lan.
 *
 * Ban ghi key duoc ghi trong CUNG transaction voi hanh dong nghiep vu. Nho vay
 * that bai thi ca hai cung rollback va client thu lai duoc — neu ghi rieng thi
 * mot lan that bai se khoa key vinh vien.
 */
@Component
public class IdempotencyService {

    private static final String SQL_FIND = """
            SELECT response_body FROM idempotency_keys
             WHERE key = :key AND user_id = CAST(:userId AS uuid) AND endpoint = :endpoint
            """;

    private static final String SQL_INSERT = """
            INSERT INTO idempotency_keys
                (key, user_id, endpoint, response_status, response_body, created_at)
            VALUES
                (:key, CAST(:userId AS uuid), :endpoint, :status,
                 CAST(:body AS jsonb), :createdAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IdempotencyService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T execute(String key, UUID userId, String endpoint,
                         Supplier<T> action, Class<T> responseType) {
        if (key == null || key.isBlank()) {
            return action.get();
        }

        List<String> daCo = jdbc.query(SQL_FIND,
                new MapSqlParameterSource()
                        .addValue("key", key)
                        .addValue("userId", userId.toString())
                        .addValue("endpoint", endpoint),
                (rs, rowNum) -> rs.getString("response_body"));

        if (!daCo.isEmpty()) {
            return objectMapper.readValue(daCo.getFirst(), responseType);
        }

        T ketQua = action.get();

        try {
            jdbc.update(SQL_INSERT, new MapSqlParameterSource()
                    .addValue("key", key)
                    .addValue("userId", userId.toString())
                    .addValue("endpoint", endpoint)
                    .addValue("status", 201)
                    .addValue("body", objectMapper.writeValueAsString(ketQua))
                    .addValue("createdAt", Timestamp.from(Instant.now())));
        } catch (DuplicateKeyException e) {
            // Hai request cung key chay song song: request kia da ghi truoc.
            // Rollback de khong tao hai booking, va bao client thu lai.
            throw new DuplicateRequestException();
        }

        return ketQua;
    }
}
