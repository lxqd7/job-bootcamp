package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record AdminModerationAuditView(
        Long id,
        String targetType,
        Long targetId,
        String action,
        Long adminId,
        String adminDisplay,
        String reason,
        LocalDateTime createdAt
) {
}