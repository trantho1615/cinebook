package com.cinebook.payment.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Ky va verify webhook bang HMAC-SHA256 tren payload THO.
 *
 * Phai ky tren chuoi tho chu khong phai tren object da parse: parse roi serialize
 * lai co the doi thu tu truong hoac dinh dang so, khien chu ky khong con khop.
 */
@Component
public class WebhookSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public WebhookSignature(@Value("${cinebook.payment.webhook-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Khong ky duoc webhook", e);
        }
    }

    public boolean verify(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        // MessageDigest.isEqual so sanh trong thoi gian hang so, khong ro ri thong tin
        // qua thoi gian phan hoi nhu String.equals.
        return MessageDigest.isEqual(
                sign(rawBody).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
