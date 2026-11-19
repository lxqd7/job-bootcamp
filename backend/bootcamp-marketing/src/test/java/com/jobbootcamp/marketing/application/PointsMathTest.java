package com.jobbootcamp.marketing.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointsMathTest {

    @Test
    void dailyAwardStopsAtCap() {
        assertThat(PointsMath.dailyAward(10, 40, 50)).isEqualTo(10);
        assertThat(PointsMath.dailyAward(10, 45, 50)).isEqualTo(5);
        assertThat(PointsMath.dailyAward(10, 50, 50)).isZero();
        assertThat(PointsMath.dailyAward(10, 60, 50)).isZero();
    }

    @Test
    void debtIsOffsetBeforeBalance() {
        PointsMath.DebtOffset offset = PointsMath.offsetDebt(3, 10);
        assertThat(offset.debtReduced()).isEqualTo(3);
        assertThat(offset.balanceAdded()).isEqualTo(7);

        PointsMath.DebtOffset excess = PointsMath.offsetDebt(15, 10);
        assertThat(excess.debtReduced()).isEqualTo(10);
        assertThat(excess.balanceAdded()).isZero();
    }

    @Test
    void negativeInputsDoNotCreatePoints() {
        assertThat(PointsMath.dailyAward(-1, 0, 50)).isZero();
        assertThat(PointsMath.offsetDebt(-1, 10).balanceAdded()).isEqualTo(10);
    }

    @Test
    void signBonusOnlyOnNamedStreakDays() {
        assertThat(PointsMath.signBonus(6)).isZero();
        assertThat(PointsMath.signBonus(7)).isEqualTo(10);
        assertThat(PointsMath.signBonus(8)).isZero();
        assertThat(PointsMath.signBonus(13)).isZero();
        assertThat(PointsMath.signBonus(14)).isEqualTo(20);
        assertThat(PointsMath.signBonus(27)).isZero();
        assertThat(PointsMath.signBonus(28)).isEqualTo(40);
        assertThat(PointsMath.signBonus(29)).isZero();
    }
}