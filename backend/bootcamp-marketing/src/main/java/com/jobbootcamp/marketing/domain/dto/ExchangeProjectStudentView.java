package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDateTime;

public record ExchangeProjectStudentView(
        Long id,
        String name,
        CouponRuleView rule,
        Integer pointsPrice,
        Integer stockRemaining,
        Integer perUserLimit,
        Integer userExchangedCount,
        boolean canExchange,
        LocalDateTime openAt,
        LocalDateTime closeAt,
        Long revision
) {
}