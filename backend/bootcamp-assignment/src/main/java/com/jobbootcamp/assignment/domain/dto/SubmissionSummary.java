package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;

public record SubmissionSummary(
        Long id,
        Integer versionNo,
        Integer score,
        Integer maxScore,
        String reviewState,
        LocalDateTime submittedAt
) {
}