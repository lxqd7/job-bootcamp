package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDateTime;

public record ExchangeRecordView(
        Long exchangeId,
        Long projectId,
        String projectName,
        Long projectRevision,
        Integer pointsSpent,
        Long userCouponId,
        CouponRuleView rule,
        String couponStatus,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Long balanceAfter,
        LocalDateTime createdAt
) {
}