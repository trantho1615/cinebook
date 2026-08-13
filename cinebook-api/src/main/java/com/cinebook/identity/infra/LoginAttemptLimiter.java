package com.cinebook.identity.infra;

import com.cinebook.identity.domain.TooManyLoginAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LoginAttemptLimiter {

    private static final String PREFIX = "login:fail:";

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(StringRedisTemplate redis,
                               @Value("${cinebook.login.max-failures}") int maxFailures,
                               @Value("${cinebook.login.lockout-window}") Duration window) {
        this.redis = redis;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    public void checkNotBlocked(String email, String ip) {
        String key = key(email, ip);
        String value = redis.opsForValue().get(key);
        if (value != null && Integer.parseInt(value) >= maxFailures) {
            Long ttl = redis.getExpire(key);
            Duration retryAfter = (ttl == null || ttl < 0)
                    ? window
                    : Duration.ofSeconds(ttl);
            throw new TooManyLoginAttemptsException(retryAfter);
        }
    }

    public void recordFailure(String email, String ip) {
        String key = key(email, ip);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // Chi dat TTL o lan dau: neu dat lai moi lan thi ke tan cong gia han
            // vinh vien cua so khoa bang cach tiep tuc thu sai, bien khoa 15 phut
            // thanh khoa vinh vien cho nan nhan.
            redis.expire(key, window);
        }
    }

    public void reset(String email, String ip) {
        redis.delete(key(email, ip));
    }

    /**
     * Dem theo cap (email, IP). Chi theo IP thi mot ke tan cong cung NAT voi nan nhan
     * co the khoa tai khoan nguoi khac. Chi theo email thi botnet de dang vuot qua.
     */
    private String key(String email, String ip) {
        return PREFIX + email + ":" + ip;
    }
}
