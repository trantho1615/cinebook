package com.cinebook.identity.infra;

import com.cinebook.identity.domain.Role;
import com.cinebook.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import com.cinebook.shared.config.SecretResolver;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class TokenService {

    private final SecretKey key;
    private final Duration accessTtl;

    public TokenService(@Value("${cinebook.jwt.secret}") String secret,
                        @Value("${cinebook.jwt.access-ttl}") Duration accessTtl) {
        this.key = Keys.hmacShaKeyFor(SecretResolver
                .hoacSinhNgauNhien(secret, "CINEBOOK_JWT_SECRET")
                .getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Dung parseSignedClaims chu khong phai parseUnsecuredClaims: chi ham nay verify
     * chu ky. Nham ham la lo hong nghiem trong, ai cung tu ky duoc token.
     *
     * Nem io.jsonwebtoken.JwtException neu chu ky sai, token hong, hoac da het han.
     */
    public AccessTokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                Role.valueOf(claims.get("role", String.class)));
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public record AccessTokenClaims(UUID userId, Role role) {
    }
}
