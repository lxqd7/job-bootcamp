package com.jobbootcamp.contracts.internal;

public record SourceLifecycleRequest(
        Long refundId,
        Long intentId,
        Long orderId,
        Long userId,
        Long expectedSourceVersion,
        String proof
) {
}