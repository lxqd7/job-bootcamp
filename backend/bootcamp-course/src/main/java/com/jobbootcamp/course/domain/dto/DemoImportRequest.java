package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record DemoImportRequest(
        @NotBlank String relativePath,
        @NotBlank String title
) {
}