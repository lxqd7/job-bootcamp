package com.jobbootcamp.assignment.domain.dto;

import java.time.LocalDateTime;

public record BoundRouteView(Long routeId, Long epochId, LocalDateTime boundAt) {
}