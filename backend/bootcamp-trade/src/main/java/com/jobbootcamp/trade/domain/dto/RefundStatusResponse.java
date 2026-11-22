package com.jobbootcamp.trade.domain.dto;

import java.time.LocalDateTime;

public record RefundStatusResponse(
        Long refundId,
        Long orderId,
        String kind,
        String state,
        long amountCent,
        Integer progressN,
        Integer progressD,
        String channelRefundNo,
        LocalDateTime acceptedAt,
        LocalDateTime updatedAt
) {
}