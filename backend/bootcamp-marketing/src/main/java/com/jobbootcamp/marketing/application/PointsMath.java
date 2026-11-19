package com.jobbootcamp.marketing.application;

public final class PointsMath {

    private PointsMath() {
    }

    public static int dailyAward(int requested, int awardedToday, int cap) {
        if (requested <= 0) {
            return 0;
        }
        int remaining = Math.max(0, cap - Math.max(0, awardedToday));
        return Math.min(requested, remaining);
    }

    public static DebtOffset offsetDebt(long debt, long reward) {
        long offset = Math.min(Math.max(0, debt), Math.max(0, reward));
        return new DebtOffset(offset, reward - offset);
    }

    public static int signBonus(int continuousDays) {
        return switch (continuousDays) {
            case 7 -> 10;
            case 14 -> 20;
            case 28 -> 40;
            default -> 0;
        };
    }

    public record DebtOffset(long debtReduced, long balanceAdded) {
    }
}