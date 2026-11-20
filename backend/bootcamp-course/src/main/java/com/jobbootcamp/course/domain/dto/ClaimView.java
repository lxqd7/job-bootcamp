package com.jobbootcamp.course.domain.dto;

public record ClaimView(
        String claimId,
        String courseId,
        String status,
        String sourceId
) {
}