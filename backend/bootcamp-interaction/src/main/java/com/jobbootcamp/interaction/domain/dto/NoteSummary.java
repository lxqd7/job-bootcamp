package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record NoteSummary(
        Long id,
        Long courseId,
        Long lessonId,
        Long positionMs,
        String type,
        String visibility,
        String state,
        String excerpt,
        String authorDisplay,
        long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean excellent,
        boolean mine
) {
}