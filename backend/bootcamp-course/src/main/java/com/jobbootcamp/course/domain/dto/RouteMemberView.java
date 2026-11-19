package com.jobbootcamp.course.domain.dto;

public record RouteMemberView(
        String courseId,
        String title,
        int sort,
        long unitPriceCent,
        boolean isFree,
        long allocatedCent,
        String epochId
) {
}