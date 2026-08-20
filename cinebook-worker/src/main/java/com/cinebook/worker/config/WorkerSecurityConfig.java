package com.cinebook.worker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Worker khong phuc vu HTTP request nghiep vu nao, nhung no VAN co spring-security tren
 * classpath (keo theo tu cinebook-api). Khong khai bao gi thi Spring Boot ap cau hinh mac
 * dinh: khoa toan bo va sinh mot mat khau ngau nhien in ra log, doi moi lan khoi dong.
 *
 * Da quan sat dung tinh huong do khi chay jar that:
 *   Using generated security password: c5bed017-...
 *   GET /actuator/info -> 401
 *
 * Tuc la endpoint giam sat khong voi toi duoc, du chinh cinebook-worker.yml khai expose no.
 * Test khong bat duoc vi test khong di qua HTTP.
 */
@Configuration
@EnableWebSecurity
public class WorkerSecurityConfig {

    @Bean
    SecurityFilterChain workerFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // denyAll chu khong phai authenticated(): worker khong co nguoi dung
                        // nao de xac thuc ca. Bat cu duong nao khac deu la mot sai lam.
                        .anyRequest().denyAll())
                .build();
    }
}
