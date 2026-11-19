package com.jobbootcamp.course.media;

public record MediaRef(
        Long id,
        String provider,
        String appId,
        String fileId,
        String demoPath,
        Long durationMs
) {
}