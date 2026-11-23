package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDateTime;

public record ExchangeProjectView(
        Long id,
        String name,
        ExchangeRuleView rule,
        Integer pointsPrice,
        Integer stockTotal,
        Integer stockUsed,
        Integer perUserLimit,
        String state,
        LocalDateTime openAt,
        LocalDateTime closeAt,
        Long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record ExchangeRuleView(
            Long id,
            String ruleKey,
            Integer versionNo,
            String type,
            String scope,
            Long targetId,
            String validityType,
            Integer validDays
    ) {
    }
}