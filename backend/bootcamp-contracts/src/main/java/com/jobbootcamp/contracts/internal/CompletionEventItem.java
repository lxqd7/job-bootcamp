package com.jobbootcamp.contracts.internal;

import java.time.Instant;

public record CompletionEventItem(
        String userId,
        String courseId,
        String lessonId,
        long completionEventSequence,
        Instant occurredAt
) {
}