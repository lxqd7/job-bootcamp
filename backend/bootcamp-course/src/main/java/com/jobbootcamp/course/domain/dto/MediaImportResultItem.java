package com.jobbootcamp.course.domain.dto;

public record MediaImportResultItem(
        String oldId,
        String mediaId,
        String status,
        String message
) {
}