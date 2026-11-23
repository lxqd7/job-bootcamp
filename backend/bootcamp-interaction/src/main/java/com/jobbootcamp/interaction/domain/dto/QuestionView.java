package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record QuestionView(
        Long id,
        Long courseId,
        Long lessonId,
        String title,
        String body,
        Long authorId,
        String authorDisplay,
        String authorRole,
        boolean anonymous,
        boolean mine,
        String state,
        long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}