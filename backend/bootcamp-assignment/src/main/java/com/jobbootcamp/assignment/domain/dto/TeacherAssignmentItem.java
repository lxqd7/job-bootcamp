package com.jobbootcamp.assignment.domain.dto;

public record TeacherAssignmentItem(
        Long id,
        String kind,
        Long chapterId,
        String status,
        Integer currentVersionNo
) {
}