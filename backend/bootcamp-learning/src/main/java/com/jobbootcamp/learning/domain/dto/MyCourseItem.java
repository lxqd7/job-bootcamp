package com.jobbootcamp.learning.domain.dto;

import java.time.LocalDateTime;

public record MyCourseItem(
        String courseId,
        String sourceId,
        String sourceType,
        LocalDateTime grantedAt
) {
}