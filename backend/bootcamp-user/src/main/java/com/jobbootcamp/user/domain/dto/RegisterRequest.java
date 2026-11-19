package com.jobbootcamp.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "用户名需为 4-32 位字母、数字或下划线")
        String username,
        @NotBlank
        String password,
        @NotBlank
        @Size(max = 30, message = "昵称最多 30 个字符")
        String nickname
) {
}