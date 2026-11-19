package com.jobbootcamp.marketing.domain.dto;

import java.time.LocalDate;

public record SignResult(
        LocalDate date,
        int basePoints,
        int bonusPoints,
        int rewardPoints,
        int continuousDays,
        boolean alreadySigned,
        long available,
        long debt,
        long monthScore
) {
}