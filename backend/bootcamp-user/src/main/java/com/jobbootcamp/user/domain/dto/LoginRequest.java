package com.jobbootcamp.user.domain.dto;

import com.jobbootcamp.user.domain.ClientApp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull ClientApp clientApp
) {
}