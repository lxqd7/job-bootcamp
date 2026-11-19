package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDate;

public record PointsAwardResult(
        long ledgerId,
        boolean duplicate,
        long rewardPoints,
        long available,
        long debt,
        long monthScore,
        LocalDate businessDate,
        String businessMonth,
        String resultCode
) {
}