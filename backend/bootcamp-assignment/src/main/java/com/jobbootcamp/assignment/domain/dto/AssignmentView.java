package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;

public record AssignmentView(
        Long id,
        String kind,
        Long courseId,
        Long chapterId,
        String status,
        Integer currentVersionNo,
        Object draftPublicBody,
        Object draftAnswerBody,
        Object currentPublicBody,
        Object currentAnswerBody,
        long revision,
        LocalDateTime updatedAt
) {
}