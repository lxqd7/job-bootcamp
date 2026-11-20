package com.jobbootcamp.trade.domain.dto;

import java.time.LocalDateTime;

public record PaymentInitiateResponse(
        Long paymentId,
        String payAction,
        String mode,
        String state,
        LocalDateTime expiresAt
) {
}