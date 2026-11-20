package com.jobbootcamp.trade.domain.dto;

import java.time.LocalDateTime;

public record AdminOrderItem(
        Long id,
        String orderNo,
        Long userId,
        String state,
        String fulfillmentState,
        long payableCent,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        Long paymentRequestId,
        String paymentRequestState
) {
}