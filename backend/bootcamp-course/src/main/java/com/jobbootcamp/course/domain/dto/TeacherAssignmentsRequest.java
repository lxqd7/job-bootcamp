package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TeacherAssignmentsRequest(
        List<Long> courseIds,
        List<Long> routeIds,
        @NotNull Long revision
) {
}