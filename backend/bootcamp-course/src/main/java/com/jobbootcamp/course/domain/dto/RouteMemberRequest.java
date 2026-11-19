package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotNull;

public record RouteMemberRequest(@NotNull Long courseId, Integer sort) {
}