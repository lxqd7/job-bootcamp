package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ActivityView(
        Long id,
        String name,
        String state,
        LocalDateTime openAt,
        LocalDateTime closeAt,
        Long revision,
        List<ActivityItemView> items
) {
    public record ActivityItemView(
            Long id,
            Long ruleId,
            CouponRuleView rule,
            Integer stockTotal,
            Integer issuedCount,
            Integer perUserLimit,
            boolean enabled,
            Long revision
    ) {
    }
}