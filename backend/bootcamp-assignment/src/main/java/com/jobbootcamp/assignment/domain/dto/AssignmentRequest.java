package com.jobbootcamp.assignment.domain.dto;

public record AssignmentRequest(
        String kind,
        Long courseId,
        Long chapterId,
        Object publicBody,
        Object answerBody,
        Long revision
) {
}