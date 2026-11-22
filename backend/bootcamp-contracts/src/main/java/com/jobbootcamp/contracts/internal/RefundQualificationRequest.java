package com.jobbootcamp.contracts.internal;

public record RefundQualificationRequest(
        Long intentId,
        Long orderId,
        Long userId,
        String snapshotHash,
        String acceptedAt,
        int completedCount,
        int totalCount
) {
}