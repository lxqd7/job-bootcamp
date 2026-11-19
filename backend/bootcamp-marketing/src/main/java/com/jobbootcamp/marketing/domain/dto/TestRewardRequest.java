package com.jobbootcamp.marketing.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TestRewardRequest(
        @NotBlank String eventId,
        @NotNull @Min(1) Long userId,
        @NotNull @Min(1) Long lessonId,
        @Min(1) @Max(50) int points,
        @NotNull Instant occurredAt
) {
}