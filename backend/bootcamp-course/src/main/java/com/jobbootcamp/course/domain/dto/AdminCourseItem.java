package com.jobbootcamp.course.domain.dto;

public record AdminCourseItem(
        String id,
        String title,
        String coverUrl,
        long priceCent,
        boolean isFree,
        String status,
        String categoryId,
        long catalogVersion,
        long revision
) {
}