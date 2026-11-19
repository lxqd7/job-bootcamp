package com.jobbootcamp.course.media;

import java.time.Instant;

public record MediaAuthorization(
        String provider,
        String playSessionId,
        Instant expiresAt,
        String appId,
        String fileId,
        String psign,
        String demoUrl,
        Long durationMs
) {
}