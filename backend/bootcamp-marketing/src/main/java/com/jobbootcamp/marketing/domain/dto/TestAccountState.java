package com.jobbootcamp.marketing.domain.dto;

public record TestAccountState(
        long available,
        long debt,
        long currentMonthScore,
        long ledgerCount,
        long outboxPending,
        long outboxAttemptMax,
        String outboxErrorCode
) {
}