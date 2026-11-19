package com.jobbootcamp.course.domain.dto;

public record MediaItemView(
        String id,
        String provider,
        String appId,
        String fileId,
        String demoPath,
        Long durationMs,
        String status,
        boolean detached,
        String origin,
        String verifiedAt,
        long revision
) {
}