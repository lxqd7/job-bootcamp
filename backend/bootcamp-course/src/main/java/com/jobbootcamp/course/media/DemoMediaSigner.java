package com.jobbootcamp.course.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * DEMO 播放 URL 的 HMAC 签名器；签名覆盖媒体、用户、会话、播放会话和过期时间，防止改参数重放。
 */
@Component
public class DemoMediaSigner {

    private final String key;

    public DemoMediaSigner(@Value("${app.media.signing-key}") String key) {
        this.key = key;
    }

    public String sign(long mediaId, long userId, long authSessionId, String playSessionId,
                       long expiresAt, String nonce) {
        return base64Url(hmac(message(mediaId, userId, authSessionId, playSessionId, expiresAt, nonce)));
    }

    public boolean matches(long mediaId, long userId, long authSessionId, String playSessionId,
                           long expiresAt, String nonce, String signature) {
        if (signature == null) {
            return false;
        }
        byte[] expected = hmac(message(mediaId, userId, authSessionId, playSessionId, expiresAt, nonce));
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private String message(long mediaId, long userId, long authSessionId, String playSessionId,
                           long expiresAt, String nonce) {
        return mediaId + "|" + userId + "|" + authSessionId + "|" + playSessionId + "|" + expiresAt + "|" + nonce;
    }

    private byte[] hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign demo media URL", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}