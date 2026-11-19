package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChapterRequest(
        @NotBlank @Size(max = 200) String title,
        Integer sort,
        Long revision
) {
}