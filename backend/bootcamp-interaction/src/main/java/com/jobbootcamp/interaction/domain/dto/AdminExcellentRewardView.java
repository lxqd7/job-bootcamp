package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record AdminExcellentRewardView(
        Long noteId,
        Long courseId,
        String noteExcerpt,
        String noteState,
        boolean excellent,
        Long authorId,
        String authorDisplay,
        Long teacherId,
        String teacherDisplay,
        Long contentRevision,
        LocalDateTime awardedAt,
        boolean revoked,
        String revokeReason,
        LocalDateTime revokedAt
) {
}