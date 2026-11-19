package com.jobbootcamp.course.domain.dto;

public record TeacherRouteItem(
        String id,
        String title,
        String status,
        long priceCent,
        int memberCount
) {
}