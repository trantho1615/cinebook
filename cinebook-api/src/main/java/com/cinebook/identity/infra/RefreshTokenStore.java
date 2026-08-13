package com.cinebook.identity.infra;

import com.cinebook.identity.domain.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refresh token KHONG phai JWT ma la 32 byte ngau nhien. Ly do: access token can
 * verify duoc ma khong tra store (JWT hop), con refresh token can thu hoi duoc
 * tuc thi (JWT khong lam duoc neu khong tra store).
 */
@Component
public class RefreshTokenStore {

    private static final String TOKEN_PREFIX = "rt:";
    private static final String FAMILY_PREFIX = "rtfam:";

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_FAMILY_ID = "familyId";
    private static final String FIELD_USED = "used";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenStore(StringRedisTemplate redis,
                             @Value("${cinebook.jwt.refresh-ttl}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    /**
     * Moi lan dang nhap tao mot family moi, nen su co tren thiet bi nay khong
     * lam van phien dang nhap tren thiet bi khac.
     */
    public String issueForNewSession(UUID userId) {
        return issue(userId, UUID.randomUUID().toString());
    }

    public Rotation rotate(String rawToken) {
        String hash = hash(rawToken);
        Map<Object, Object> entry = redis.opsForHash().entries(TOKEN_PREFIX + hash);
        if (entry.isEmpty()) {
            throw new InvalidRefreshTokenException();
        }

        String familyId = (String) entry.get(FIELD_FAMILY_ID);
        if (Boolean.parseBoolean((String) entry.get(FIELD_USED))) {
            // Token nay da duoc xoay vong roi ma van bi dung lai: chuoi token da bi sao chep.
            // Khong biet ke tan cong dang giu token nao, nen huy ca family.
            revokeFamily(familyId);
            throw new InvalidRefreshTokenException();
        }

        redis.opsForHash().put(TOKEN_PREFIX + hash, FIELD_USED, "true");
        UUID userId = UUID.fromString((String) entry.get(FIELD_USER_ID));
        return new Rotation(userId, issue(userId, familyId));
    }

    public void revokeFamilyOf(String rawToken) {
        Object familyId = redis.opsForHash().get(TOKEN_PREFIX + hash(rawToken), FIELD_FAMILY_ID);
        if (familyId != null) {
            revokeFamily((String) familyId);
        }
    }

    private String issue(UUID userId, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(rawToken);

        redis.opsForHash().putAll(TOKEN_PREFIX + hash, Map.of(
                FIELD_USER_ID, userId.toString(),
                FIELD_FAMILY_ID, familyId,
                FIELD_USED, "false"));
        redis.expire(TOKEN_PREFIX + hash, ttl);

        redis.opsForSet().add(FAMILY_PREFIX + familyId, hash);
        redis.expire(FAMILY_PREFIX + familyId, ttl);

        return rawToken;
    }

    private void revokeFamily(String familyId) {
        Set<String> hashes = redis.opsForSet().members(FAMILY_PREFIX + familyId);
        if (hashes != null) {
            hashes.forEach(h -> redis.delete(TOKEN_PREFIX + h));
        }
        redis.delete(FAMILY_PREFIX + familyId);
    }

    /**
     * Chi ban bam duoc luu. Redis bi lo thi ke tan cong van khong co token dung duoc,
     * cung ly do voi viec khong luu mat khau dang tho.
     */
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM khong ho tro SHA-256", e);
        }
    }

    public record Rotation(UUID userId, String newRawToken) {
    }
}
