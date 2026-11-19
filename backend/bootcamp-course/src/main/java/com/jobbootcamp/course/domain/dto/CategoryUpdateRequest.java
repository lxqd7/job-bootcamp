package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        Long parentId,
        Integer sort,
        Boolean enabled,
        @NotNull Long revision
) {
}