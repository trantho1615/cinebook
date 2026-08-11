package com.cinebook.db;

import com.cinebook.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineMigrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flyway_da_chay_it_nhat_mot_migration_thanh_cong() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void extension_btree_gist_da_duoc_cai() {
        Integer installed = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'",
                Integer.class);

        assertThat(installed).isEqualTo(1);
    }
}
