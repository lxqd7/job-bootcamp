package com.jobbootcamp.course.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MediaImportRequest(
        @NotBlank String batchId,
        @NotBlank String inputHash,
        @NotEmpty List<String> selectedOldIds
) {
}