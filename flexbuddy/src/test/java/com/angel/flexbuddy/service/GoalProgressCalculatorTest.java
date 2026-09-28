package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.dto.GoalProgress;

class GoalProgressCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    private final GoalProgressCalculator calculator = new GoalProgressCalculator();

    @Test
    void thursdayWithAScheduledBlockIsOnTrackByPace() {
        GoalProgress week = progress("600", "412.50", "84.00", "78.40", LocalDate.of(2026, 9, 10));

        assertThat(week.percent()).isEqualByComparingTo("68.8");
        assertThat(week.remaining()).isEqualByComparingTo("103.50");
        assertThat(week.blocksToGo()).isEqualTo(2);
        assertThat(week.onTrack()).isTrue();
        assertThat(week.periodStart()).isEqualTo(MONDAY);
        assertThat(week.periodEnd()).isEqualTo(SUNDAY);
    }

    @Test
    void paceIsJudgedAgainstTheDaysGoneSoFar() {
        assertThat(progress("600", "100.00", "0", "80", MONDAY).onTrack()).isTrue();
        assertThat(progress("600", "300.00", "0", "80", LocalDate.of(2026, 9, 12)).onTrack()).isFalse();
    }

    @Test
    void scheduledPayCanPutABehindWeekBackOnTrack() {
        GoalProgress week = progress("600", "500.00", "100.00", "80", SUNDAY);

        assertThat(week.onTrack()).isTrue();
        assertThat(week.remaining()).isEqualByComparingTo("0.00");
        assertThat(week.blocksToGo()).isZero();
    }

    @Test
    void blocksToGoRoundsUpToWholeBlocks() {
        assertThat(progress("600", "443.20", "0", "78.40", MONDAY).blocksToGo()).isEqualTo(2);
        assertThat(progress("600", "443.19", "0", "78.40", MONDAY).blocksToGo()).isEqualTo(3);
        assertThat(progress("600", "0", "0", null, MONDAY).blocksToGo()).isZero();
    }

    @Test
    void goingPastTheGoalShowsMoreThanOneHundredPercentAndNothingRemaining() {
        GoalProgress week = progress("600", "604.50", "0", "80", SUNDAY);

        assertThat(week.percent()).isEqualByComparingTo("100.8");
        assertThat(week.remaining()).isEqualByComparingTo("0.00");
    }

    @Test
    void noGoalMeansNoProgress() {
        assertThat(calculator.progress(null, BigDecimal.TEN, BigDecimal.ZERO, null, null, MONDAY, SUNDAY, MONDAY)).isNull();
    }

    private GoalProgress progress(String goal, String earned, String planned, String averageBlock, LocalDate today) {
        return calculator.progress(new BigDecimal(goal), new BigDecimal(earned), new BigDecimal(planned),
                averageBlock == null ? null : new BigDecimal(averageBlock), 240, MONDAY, SUNDAY, today);
    }
}
