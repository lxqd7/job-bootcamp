package com.jobbootcamp.contracts.internal;

public record BenefitAccessCheckResponse(
        boolean allowed,
        boolean courseAllowed,
        Long matchedRouteId,
        Long matchedEpochId,
        Long sourceId
) {
}