package com.jobbootcamp.contracts.internal;

import java.util.List;

public record BenefitAccessCheckRequest(Long userId, Long courseId, List<RouteEpoch> routes) {

    public record RouteEpoch(Long routeId, Long epochId) {
    }
}