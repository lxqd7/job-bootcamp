package com.jobbootcamp.course.domain.dto;

import java.util.List;

public record MediaImportResponse(
        String operationKey,
        String batchId,
        List<MediaImportResultItem> items
) {
}