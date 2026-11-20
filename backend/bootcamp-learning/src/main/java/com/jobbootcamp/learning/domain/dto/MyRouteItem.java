package com.jobbootcamp.learning.domain.dto;

import java.time.LocalDateTime;

public record MyRouteItem(
        String routeId,
        String sourceId,
        boolean futureCourses,
        LocalDateTime acquiredAt
) {
}