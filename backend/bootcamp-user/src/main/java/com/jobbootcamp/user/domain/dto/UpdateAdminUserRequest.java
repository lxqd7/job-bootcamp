package com.jobbootcamp.user.domain.dto;

import com.jobbootcamp.user.domain.RoleCode;
import jakarta.validation.constraints.NotNull;

public record UpdateAdminUserRequest(
        Boolean enabled,
        RoleCode role,
        @NotNull Long revision
) {
}