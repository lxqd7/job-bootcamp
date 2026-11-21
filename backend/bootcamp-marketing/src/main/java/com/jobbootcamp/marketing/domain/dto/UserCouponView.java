package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDateTime;

public record UserCouponView(
        Long id,
        CouponRuleView rule,
        String sourceType,
        Long sourceGroupId,
        Long sourceItemId,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        String status,
        String displayStatus,
        Long lockedOrderId,
        Long usedOrderId,
        LocalDateTime createdAt
) {
}