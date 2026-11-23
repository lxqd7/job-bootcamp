package com.jobbootcamp.interaction.domain.dto;

import java.time.LocalDateTime;

public record AdminContentItem(
        String type,
        Long id,
        Long courseId,
        String state,
        String summary,
        String authorDisplay,
        LocalDateTime createdAt
) {
}