package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourseCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String description,
        @NotNull Long categoryId,
        @NotNull Long priceCent,
        @NotNull Boolean isFree,
        @Size(max = 1000) String coverUrl
) {
}