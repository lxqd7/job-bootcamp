package com.jobbootcamp.contracts.internal;

public record RefundQualificationResponse(
        String state,
        String reason,
        long amountCent,
        String paidAt
) {
}