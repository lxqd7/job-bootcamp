package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        Long parentId,
        @NotBlank @Size(max = 200) String name,
        Integer sort,
        Boolean enabled
) {
}