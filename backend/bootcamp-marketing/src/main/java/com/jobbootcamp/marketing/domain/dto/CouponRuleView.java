package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDateTime;

public record CouponRuleView(
        Long id,
        String ruleKey,
        Integer versionNo,
        String type,
        String scope,
        Long targetId,
        Long thresholdCent,
        Long amountCent,
        Integer payRateBps,
        Long maxDiscountCent,
        String validityType,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer validDays,
        boolean issued,
        Long revision
) {
}