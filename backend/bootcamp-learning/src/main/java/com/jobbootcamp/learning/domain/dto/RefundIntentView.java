package com.jobbootcamp.learning.domain.dto;

import java.time.LocalDateTime;

public record RefundIntentView(
        String requestId,
        Long orderId,
        LocalDateTime acceptedAt,
        long sequence,
        String state,
        String reason,
        int completedCount,
        int totalCount
) {
}