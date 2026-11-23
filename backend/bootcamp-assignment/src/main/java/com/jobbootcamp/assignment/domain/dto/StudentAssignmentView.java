package com.jobbootcamp.assignment.domain.dto;

public record StudentAssignmentView(
        Long id,
        String kind,
        Long courseId,
        Long chapterId,
        String status,
        Long currentVersionId,
        Integer currentVersionNo,
        Object publicBody,
        boolean currentVersionSubmitted,
        Long openAttemptId
) {
}