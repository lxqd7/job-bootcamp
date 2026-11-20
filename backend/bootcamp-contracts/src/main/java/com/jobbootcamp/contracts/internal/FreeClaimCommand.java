package com.jobbootcamp.contracts.internal;

import java.time.Instant;

public record FreeClaimCommand(
        String claimId,
        Long userId,
        Long courseId,
        Long courseVersion,
        Instant acceptedAt
) {
}