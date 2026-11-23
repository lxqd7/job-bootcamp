package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;

public record TeacherSubmissionSummary(
        Long id,
        Long assignmentId,
        String kind,
        Long chapterId,
        Long studentId,
        String studentNickname,
        Integer versionNo,
        Integer score,
        Integer maxScore,
        String reviewState,
        LocalDateTime submittedAt
) {
}