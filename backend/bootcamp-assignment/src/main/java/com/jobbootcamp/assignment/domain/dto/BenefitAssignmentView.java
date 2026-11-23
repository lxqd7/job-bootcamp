package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BenefitAssignmentView(
        Long id,
        String kind,
        Long courseId,
        String status,
        Integer currentVersionNo,
        Object draftPublicBody,
        Object draftAnswerBody,
        Object currentPublicBody,
        Object currentAnswerBody,
        long revision,
        LocalDateTime updatedAt,
        List<BoundRouteView> routes
) {
}