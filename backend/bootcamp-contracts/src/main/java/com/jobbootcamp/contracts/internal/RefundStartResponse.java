package com.jobbootcamp.contracts.internal;

public record RefundStartResponse(
        Long refundId,
        String state
) {
}