package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record RevokeRewardView(
        Long noteId,
        long rewardVersion,
        boolean revoked,
        LocalDateTime revokedAt,
        String reason
) {
}