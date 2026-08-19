package com.cinebook.identity.infra;

import com.cinebook.shared.web.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Configuration
@EnableWebSecurity
// Thieu annotation nay thi @PreAuthorize bi BO QUA HOAN TOAN, khong bao loi gi —
// endpoint admin se mo cho moi nguoi da dang nhap. Da kiem chung bang cach tam go
// annotation: test customer_khong_goi_duoc_endpoint_danh_cho_admin do ngay.
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // API stateless dung Bearer token: khong co cookie phien nen khong co
                // be mat tan cong CSRF de bao ve.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /auth/refresh va /auth/logout phai cong khai vi client goi chung
                        // KHI access token da het han. Doi access token hop le thi co che
                        // refresh tro nen vo nghia. Refresh token la thu xac thuc cho hai
                        // loi goi nay.
                        .requestMatchers("/auth/register", "/auth/login",
                                "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Webhook den tu cong thanh toan, khong co token cua nguoi dung.
                        // Chu ky HMAC la thu xac thuc cho endpoint nay.
                        .requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()
                        // Duyet phim, rap va lich chieu khong can dang nhap — khach vang lai
                        // phai xem duoc truoc khi quyet dinh tao tai khoan. Chi mo GET;
                        // duong ghi nam duoi /admin/** va van duoc @PreAuthorize canh.
                        .requestMatchers(HttpMethod.GET,
                                "/movies/**", "/cinemas/**", "/showtimes/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Khong co entry point tuy chinh thi Spring Security mac dinh chuyen huong
                // toi trang dang nhap HTML — sai hoan toan voi mot API JSON.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Can dang nhap de truy cap tai nguyen nay"))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "Ban khong co quyen truy cap tai nguyen nay")))
                .build();
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
