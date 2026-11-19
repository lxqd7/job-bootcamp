package com.jobbootcamp.marketing.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DebtFixtureRequest(
        @NotNull @Min(1) Long userId,
        @NotNull @Min(0) Long debt
) {
}