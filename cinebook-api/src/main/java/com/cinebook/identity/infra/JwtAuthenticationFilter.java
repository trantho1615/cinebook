package com.cinebook.identity.infra;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final TokenService tokens;

    public JwtAuthenticationFilter(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            try {
                TokenService.AccessTokenClaims claims = tokens.parse(header.substring(PREFIX.length()));
                var authority = new SimpleGrantedAuthority("ROLE_" + claims.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.userId(), null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // Token hong thi coi nhu chua dang nhap. Khong nem loi o day:
                // quyet dinh tra 401 hay khong thuoc ve tang phan quyen phia sau.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
