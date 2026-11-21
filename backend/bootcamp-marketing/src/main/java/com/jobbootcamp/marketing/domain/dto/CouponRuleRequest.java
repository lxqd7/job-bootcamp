package com.jobbootcamp.marketing.domain.dto;

public record CouponRuleRequest(
        String ruleKey,
        String type,
        String scope,
        Long targetId,
        Long thresholdCent,
        Long amountCent,
        Integer payRateBps,
        Long maxDiscountCent,
        String validityType,
        String validFrom,
        String validUntil,
        Integer validDays,
        Long revision
) {
}