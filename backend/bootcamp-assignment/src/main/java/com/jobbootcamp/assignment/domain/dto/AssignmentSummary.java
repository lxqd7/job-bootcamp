package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;

public record AssignmentSummary(
        Long id,
        String kind,
        Long courseId,
        Long chapterId,
        String status,
        Integer currentVersionNo,
        long revision,
        LocalDateTime updatedAt
) {
}