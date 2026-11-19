package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PointsLedgerView(
        long id,
        String type,
        long rewardPoints,
        long balanceDelta,
        long debtDelta,
        long rankDelta,
        long balanceAfter,
        long debtAfter,
        LocalDate businessDate,
        String businessMonth,
        LocalDateTime occurredAt,
        String resultCode
) {
}