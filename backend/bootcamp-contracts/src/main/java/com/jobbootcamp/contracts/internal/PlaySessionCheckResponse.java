package com.jobbootcamp.contracts.internal;

public record PlaySessionCheckResponse(
        boolean allowed,
        Long courseId,
        Long lessonId,
        Long mediaId,
        Long durationMs,
        String mode
) {
}