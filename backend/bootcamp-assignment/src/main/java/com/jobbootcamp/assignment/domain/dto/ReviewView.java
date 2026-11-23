package com.jobbootcamp.assignment.domain.dto;

public record ReviewView(
        String taskStatus,
        Object feedback,
        String safeErrorCode,
        Boolean isMock
) {
}