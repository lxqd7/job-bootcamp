package com.jobbootcamp.learning.application;

import java.time.LocalDateTime;

public record RouteEpochChange(Long courseId, Long epochId, LocalDateTime joinedAt, boolean free,
                               long routeVersion) {
}