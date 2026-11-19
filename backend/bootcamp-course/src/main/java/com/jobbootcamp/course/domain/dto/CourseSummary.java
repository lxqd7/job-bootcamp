package com.jobbootcamp.course.domain.dto;

public record CourseSummary(
        String id,
        String title,
        String coverUrl,
        long priceCent,
        boolean isFree,
        String status,
        String categoryId
) {
}