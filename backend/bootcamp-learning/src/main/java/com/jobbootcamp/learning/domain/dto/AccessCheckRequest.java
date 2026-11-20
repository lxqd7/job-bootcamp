package com.jobbootcamp.learning.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AccessCheckRequest(
        @NotNull @Min(1) Long userId,
        @NotNull @Min(1) Long courseId
) {
}