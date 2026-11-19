package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record MediaImportPreviewResponse(
        String operationKey,
        String batchId,
        String inputHash,
        List<MediaPreviewItem> items,
        List<String> duplicates,
        List<String> missing
) {
}