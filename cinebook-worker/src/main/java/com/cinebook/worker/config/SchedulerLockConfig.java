package com.cinebook.worker.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
// defaultLockAtMostFor la luoi an toan: neu tien trinh chet giua chung khi dang giu khoa,
// khoa tu het han sau khoang nay thay vi ket vinh vien.
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        // Khong co usingDbTime(), ShedLock so gio bang dong ho cua may ung
                        // dung. Hai may lech nhau vai giay la du de ca hai cung tin rang
                        // khoa da het han. Dung gio cua DB thi chi co mot dong ho.
                        .usingDbTime()
                        .build());
    }
}
