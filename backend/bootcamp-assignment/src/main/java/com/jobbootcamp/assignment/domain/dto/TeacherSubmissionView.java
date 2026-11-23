package com.jobbootcamp.assignment.domain.dto;

public record TeacherSubmissionView(
        Long submissionId,
        Long assignmentId,
        String kind,
        Long studentId,
        String studentNickname,
        SubmissionView submission,
        ReviewView review
) {
}