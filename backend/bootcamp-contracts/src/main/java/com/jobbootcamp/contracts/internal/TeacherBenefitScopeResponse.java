package com.jobbootcamp.contracts.internal;

import java.util.List;

public record TeacherBenefitScopeResponse(
        boolean courseAssigned,
        boolean allRoutesAssigned,
        List<RouteScope> routes
) {

    public record RouteScope(
            Long routeId,
            Long epochId,
            boolean routeAssigned,
            boolean routeOnline,
            boolean containsCourse,
            String reason
    ) {
    }
}