package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDateTime;

public record ExchangeResult(
        Long exchangeId,
        Long projectId,
        Long projectRevision,
        Integer pointsSpent,
        Long userCouponId,
        Long balance,
        LocalDateTime createdAt
) {
}