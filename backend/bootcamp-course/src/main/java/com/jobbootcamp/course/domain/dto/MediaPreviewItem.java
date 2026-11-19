package com.jobbootcamp.course.domain.dto;

public record MediaPreviewItem(
        String oldId,
        String action,
        String mediaId,
        String reason
) {
}