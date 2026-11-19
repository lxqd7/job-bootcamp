package com.jobbootcamp.marketing.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MonthScoreFixtureRequest(
        @NotNull @Min(1) Long userId,
        @NotBlank String month,
        @Min(0) long score
) {
}