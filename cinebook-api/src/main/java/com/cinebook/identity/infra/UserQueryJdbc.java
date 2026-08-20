package com.cinebook.identity.infra;

import com.cinebook.identity.api.UserLookup;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserQueryJdbc implements UserLookup {

    private static final String SQL_EMAIL = """
            SELECT email FROM users WHERE id = CAST(:userId AS uuid)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public UserQueryJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findEmail(UUID userId) {
        return jdbc.query(SQL_EMAIL,
                        new MapSqlParameterSource().addValue("userId", userId.toString()),
                        (rs, n) -> rs.getString(1))
                .stream()
                .findFirst();
    }
}
