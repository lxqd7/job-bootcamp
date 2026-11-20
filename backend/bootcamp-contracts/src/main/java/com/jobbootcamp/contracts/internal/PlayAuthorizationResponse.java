package com.jobbootcamp.contracts.internal;

import java.time.Instant;

public record PlayAuthorizationResponse(
        String provider,
        String playSessionId,
        Instant expiresAt,
        String appId,
        String fileId,
        String psign,
        String demoUrl,
        Long durationMs,
        Long courseId
) {
}