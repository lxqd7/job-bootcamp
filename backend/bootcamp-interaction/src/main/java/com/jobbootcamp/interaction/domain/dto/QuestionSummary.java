package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record QuestionSummary(
        Long id,
        Long courseId,
        Long lessonId,
        String title,
        String authorDisplay,
        String authorRole,
        boolean anonymous,
        boolean mine,
        int answerCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}