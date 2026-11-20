package com.jobbootcamp.learning.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProgressRequest(
        @NotNull @Min(0) Long positionMs,
        @NotBlank String playSessionId
) {
}