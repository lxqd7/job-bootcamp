package com.jobbootcamp.marketing.domain.dto;

public record PointsSpendResult(
        Long ledgerId,
        Integer pointsSpent,
        Long available,
        Long debt
) {
}