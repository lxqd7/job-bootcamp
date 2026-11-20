package com.jobbootcamp.learning.domain.dto;

import java.time.LocalDateTime;

public record ProgressResponse(
        boolean completed,
        boolean firstCompletion,
        LocalDateTime completedAt,
        long courseCompletedCount,
        long totalCount
) {
}