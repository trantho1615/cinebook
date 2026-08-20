package com.cinebook.identity.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Mo /demo/** cho cong thanh toan gia lap — CHI o profile "demo".
 *
 * Vi sao la mot chuoi filter rieng chu khong them mot dong vao SecurityConfig chinh:
 * chuoi chinh dung cho moi moi truong, va mot dong permitAll("/demo/**") nam trong do la
 * thu ai cung co the quen go ra truoc khi deploy. O day thi khong co gi de quen — khong
 * bat profile demo la bean nay khong ton tai.
 */
@Configuration
@Profile("demo")
public class DemoSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain demoFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/demo/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
