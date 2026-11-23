package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record AnswerView(
        Long id,
        Long questionId,
        String body,
        String authorDisplay,
        String authorRole,
        boolean mine,
        int commentCount,
        long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}