package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;

public record SubmissionView(
        Long id,
        Long assignmentId,
        Long versionId,
        Integer versionNo,
        Object publicBody,
        Object answers,
        Integer score,
        Integer maxScore,
        String reviewState,
        LocalDateTime submittedAt,
        Object referenceBySlot
) {
}