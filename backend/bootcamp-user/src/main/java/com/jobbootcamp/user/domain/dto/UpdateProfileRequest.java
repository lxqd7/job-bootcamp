package com.jobbootcamp.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank
        @Size(max = 30, message = "昵称最多 30 个字符")
        String nickname,
        @Size(max = 1000, message = "头像地址最多 1000 个字符")
        String avatarUrl,
        @NotNull
        Long revision
) {
}