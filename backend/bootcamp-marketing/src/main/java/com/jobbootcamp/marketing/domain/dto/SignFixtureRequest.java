package com.jobbootcamp.marketing.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SignFixtureRequest(@NotNull @Min(1) Long userId) {
}