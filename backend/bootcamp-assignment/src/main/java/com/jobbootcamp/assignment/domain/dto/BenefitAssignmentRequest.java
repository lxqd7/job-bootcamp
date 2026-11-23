package com.jobbootcamp.assignment.domain.dto;

import java.util.List;

public record BenefitAssignmentRequest(
        String kind,
        Long courseId,
        List<Long> routeIds,
        Object publicBody,
        Object answerBody,
        Long revision
) {
}