package com.jobbootcamp.marketing.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SignDaysFixtureRequest(
        @NotNull @Min(1) Long userId,
        @Min(1) @Max(60) int days,
        @NotNull LocalDate endDate
) {
}