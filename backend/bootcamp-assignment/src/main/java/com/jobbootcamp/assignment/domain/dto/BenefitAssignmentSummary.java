package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BenefitAssignmentSummary(
        Long id,
        String kind,
        Long courseId,
        String status,
        Integer currentVersionNo,
        long revision,
        LocalDateTime updatedAt,
        List<Long> routeIds
) {
}