package com.jobbootcamp.trade.domain.dto;

import java.time.LocalDateTime;

public record RefundResponse(
        Long id,
        String refundNo,
        Long orderId,
        Long paymentId,
        String kind,
        String state,
        long amountCent,
        LocalDateTime acceptedAt,
        String channelRefundNo
) {
}