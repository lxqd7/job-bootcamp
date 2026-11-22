package com.jobbootcamp.trade.domain.dto;

import java.time.LocalDateTime;

public record AdminRefundItem(
        Long id,
        String refundNo,
        Long orderId,
        Long userId,
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