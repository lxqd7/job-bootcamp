package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LessonRequest(
        @NotBlank @Size(max = 200) String title,
        Integer sort,
        Long mediaId,
        Long revision
) {
}