package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record RouteDetail(
        String id,
        String title,
        String description,
        long priceCent,
        String status,
        long currentVersionNo,
        long revision,
        boolean draft,
        List<RouteMemberView> members
) {
}