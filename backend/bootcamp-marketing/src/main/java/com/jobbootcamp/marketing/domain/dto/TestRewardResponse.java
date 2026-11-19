package com.jobbootcamp.marketing.domain.dto;

public record TestRewardResponse(
        boolean inboxDuplicate,
        long ledgerId,
        boolean ledgerDuplicate,
        long rewardPoints,
        long available,
        long debt,
        long monthScore,
        String businessDate,
        String businessMonth,
        String resultCode
) {
}