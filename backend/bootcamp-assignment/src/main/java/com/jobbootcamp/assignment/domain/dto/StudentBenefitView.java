package com.jobbootcamp.assignment.domain.dto;

public record StudentBenefitView(
        Long id,
        String kind,
        Long courseId,
        String status,
        Integer currentVersionNo,
        Object publicBody,
        Long routeId,
        Long epochId,
        boolean currentVersionSubmitted,
        Long openAttemptId
) {
}