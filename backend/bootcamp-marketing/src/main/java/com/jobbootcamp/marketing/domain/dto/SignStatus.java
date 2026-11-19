package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDate;
import java.util.List;

public record SignStatus(
        String month,
        LocalDate date,
        boolean signedToday,
        int continuousDays,
        int todayPoints,
        long monthSignedDays,
        List<Integer> days,
        long available,
        long debt,
        long currentMonthScore
) {
}