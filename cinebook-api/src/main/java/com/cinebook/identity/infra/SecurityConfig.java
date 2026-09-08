package com.cinebook.identity.infra;

import com.cinebook.shared.web.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
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

    /**
     * Duyet phim, rap va lich chieu: khach vang lai phai xem duoc truoc khi quyet dinh tao
     * tai khoan.
     */
    private static final String[] DUYET_CONG_KHAI = {
            "/movies/**", "/cinemas/**", "/showtimes/**"
    };

    /**
     * Duong dan cua UI demo. Liet ke dung ten chu khong dung mot dau sao chung chung:
     * "/**" se nuot ca nhung endpoint chua duoc liet ke o tren no.
     */
    private static final String[] FILE_TINH = {
            "/", "/index.html",
            "/css/**", "/js/**", "/vendor/**", "/fonts/**", "/favicon.ico"
    };

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * Chuoi rieng cho actuator, dat truoc chuoi chinh.
     *
     * Voi management.server.port = 8090, cac endpoint nay CHI ton tai tren cong quan tri —
     * chuoi nay khong mo them gi tren cong 8080. Thu bao ve chung la mang: cong quan tri
     * khong duoc public ra ngoai. MetricsExposureTest.cong_nghiep_vu_khong_lo_metric canh
     * dieu do, nen neu mot ngay nao do ai go management.server.port di, test se do.
     */
    @Bean
    @Order(0)
    SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
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
                        // Webhook den tu cong thanh toan, khong co token cua nguoi dung.
                        // Chu ky HMAC la thu xac thuc cho endpoint nay.
                        .requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()
                        // Chi mo duong DOC; duong ghi nam duoi /admin/** va van duoc
                        // @PreAuthorize canh.
                        //
                        // Vi sao ca GET va HEAD: RFC 9110 muc 9.3.2 doi HEAD tra loi giong
                        // het GET tru phan than, va cac dich vu giam sat uptime mac dinh gui
                        // HEAD. Truoc day chi liet ke GET, nen HEAD roi xuong
                        // anyRequest().authenticated() va an 401 — trang van song ma monitor
                        // bao chet. Phat hien luc kiem chung deploy that bang `curl -I`.
                        // HEAD khong lo them gi: no chi tra ve header cua mot GET da cong khai.
                        // HeadRequestTest canh ca hai chieu cua chuyen nay.
                        .requestMatchers(HttpMethod.GET, DUYET_CONG_KHAI).permitAll()
                        .requestMatchers(HttpMethod.HEAD, DUYET_CONG_KHAI).permitAll()
                        .requestMatchers(HttpMethod.GET, FILE_TINH).permitAll()
                        .requestMatchers(HttpMethod.HEAD, FILE_TINH).permitAll()
                        // Kenh realtime cua so do ghe. Cong khai vi chinh so do ghe da
                        // cong khai (GET /showtimes/** o tren) — no chi mang mau ghe, khong
                        // mang thong tin cua ai ca.
                        .requestMatchers("/ws/**").permitAll()
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
