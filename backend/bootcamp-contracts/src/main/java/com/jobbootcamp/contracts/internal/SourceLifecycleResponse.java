package com.jobbootcamp.contracts.internal;

public record SourceLifecycleResponse(
        Long sourceId,
        String state,
        Long sourceVersion
) {
}