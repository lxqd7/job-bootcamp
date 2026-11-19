package com.jobbootcamp.course.domain.dto;

public record MediaOperationView(
        String operationKey,
        String kind,
        String state,
        String inputHash,
        String resultJson
) {
}