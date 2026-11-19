package com.jobbootcamp.course.media;

import com.jobbootcamp.course.application.SimpleJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class VodPlaySigner {

    private final String urlKey;
    private final String pcfg;

    public VodPlaySigner(@Value("${app.media.vod.url-key}") String urlKey,
                         @Value("${app.media.vod.pcfg}") String pcfg) {
        this.urlKey = urlKey;
        this.pcfg = pcfg;
    }

    public String sign(String appId, String fileId, Long userId, Long authSessionId, Instant expiresAt) {
        if (urlKey == null || urlKey.isBlank()) {
            throw new IllegalStateException("VOD url-key is not configured");
        }
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appId", appId);
        payload.put("fileId", fileId);
        payload.put("currentTimeStamp", now);
        payload.put("pcfg", pcfg);
        Map<String, Object> accessInfo = new LinkedHashMap<>();
        accessInfo.put("exper", Math.max(1, expiresAt.getEpochSecond() - now));
        if (userId != null) {
            accessInfo.put("uid", String.valueOf(userId));
        }
        payload.put("urlAccessInfo", accessInfo);

        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = base64Url(SimpleJson.write(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + body;
        return signingInput + "." + base64Url(hmac(signingInput));
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(urlKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign VOD play URL", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}