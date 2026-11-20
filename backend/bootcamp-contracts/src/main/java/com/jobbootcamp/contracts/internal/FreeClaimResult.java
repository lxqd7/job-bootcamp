package com.jobbootcamp.contracts.internal;

public record FreeClaimResult(
        String claimId,
        String courseId,
        String sourceId,
        String state,
        boolean alreadyGranted
) {
}