package com.jobbootcamp.contracts.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 服务间短期凭据：HMAC-SHA256 签名的紧凑载荷，用于内部接口调用。
 */
public final class InternalTokenCodec {

    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";

    private InternalTokenCodec() {
    }

    public static String issue(String secret, String issuer, String audience, String operation, long ttlSeconds) {
        requireText(secret, "secret");
        requireText(issuer, "issuer");
        requireText(audience, "audience");
        requireText(operation, "operation");
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = String.join("|", VERSION, issuer, audience, operation,
                Long.toString(expiresAt), UUID.randomUUID().toString());
        String signature = base64Url(hmac(secret, payload));
        return base64Url(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    public static void verify(String secret, String token, String expectedAudience, String requiredOperation) {
        requireText(secret, "secret");
        if (token == null || token.isBlank()) {
            throw new InternalTokenException("service token is missing");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            throw new InternalTokenException("service token format is invalid");
        }
        String payload;
        byte[] presentedSignature;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            presentedSignature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InternalTokenException("service token encoding is invalid");
        }
        byte[] expectedSignature = hmac(secret, payload);
        if (!MessageDigest.isEqual(expectedSignature, presentedSignature)) {
            throw new InternalTokenException("service token signature is invalid");
        }

        String[] fields = payload.split("\\|", -1);
        if (fields.length != 6 || !VERSION.equals(fields[0])) {
            throw new InternalTokenException("service token payload is invalid");
        }
        String audience = fields[2];
        String operation = fields[3];
        long expiresAt;
        try {
            expiresAt = Long.parseLong(fields[4]);
        } catch (NumberFormatException e) {
            throw new InternalTokenException("service token expiry is invalid");
        }
        if (!expectedAudience.equals(audience)) {
            throw new InternalTokenException("service token audience is invalid");
        }
        if (!requiredOperation.equals(operation)) {
            throw new InternalTokenException("service token operation is not allowed");
        }
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new InternalTokenException("service token is expired");
        }
    }

    private static byte[] hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign or verify service token", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}