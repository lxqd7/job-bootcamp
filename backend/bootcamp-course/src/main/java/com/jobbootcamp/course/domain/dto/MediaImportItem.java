package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record MediaImportItem(
        @NotBlank String oldId,
        @NotBlank String appId,
        @NotBlank String fileId,
        Object courseMapping
) {
}