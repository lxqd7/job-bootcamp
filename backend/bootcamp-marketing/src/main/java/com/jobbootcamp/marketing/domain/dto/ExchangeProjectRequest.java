package com.jobbootcamp.marketing.domain.dto;

public record ExchangeProjectRequest(
        String name,
        Long ruleId,
        Integer pointsPrice,
        Integer stockTotal,
        Integer perUserLimit,
        String openAt,
        String closeAt,
        Long revision
) {
}