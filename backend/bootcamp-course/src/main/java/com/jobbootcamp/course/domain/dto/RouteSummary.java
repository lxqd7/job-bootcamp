package com.jobbootcamp.course.domain.dto;

public record RouteSummary(
        String id,
        String title,
        long priceCent,
        String status,
        int memberCount,
        long revision
) {
}