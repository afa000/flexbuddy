package com.angel.flexbuddy.importcore.figures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RatesGoalsAndPeriodsTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);
    private static final Set<DayOfWeek> TUESDAY_FRIDAY = EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);

    @Test
    void irsRatesChangeOnTheirDates() {
        assertEquals(new BigDecimal("0.670"), IrsMileageRates.rateOn(LocalDate.of(2023, 5, 1)));
        assertEquals(new BigDecimal("0.725"), IrsMileageRates.rateOn(LocalDate.of(2026, 6, 30)));
        assertEquals(new BigDecimal("0.760"), IrsMileageRates.rateOn(LocalDate.of(2026, 7, 1)));
        assertEquals(new BigDecimal("0.760"), IrsMileageRates.rateOn(null));
        assertEquals(LocalDate.of(2026, 7, 1), IrsMileageRates.sinceOn(LocalDate.of(2026, 8, 1)));
        assertEquals(List.of(new BigDecimal("0.725"), new BigDecimal("0.760")),
                IrsMileageRates.ratesBetween(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31)));
    }

    @Test
    void aSingleRateExistsOnlyWhenNothingChangesInTheRange() {
        LocalDate june = LocalDate.of(2026, 6, 1);
        LocalDate july = LocalDate.of(2026, 7, 31);

        assertNull(MileageRates.irs().singleRate(june, july));
        assertEquals(new BigDecimal("0.725"), MileageRates.irs().singleRate(june, LocalDate.of(2026, 6, 30)));
        assertEquals(new BigDecimal("0.650"), MileageRates.fixed(new BigDecimal("0.650")).singleRate(june, july));
        assertEquals(new BigDecimal("0.650"), MileageRates.fixed(new BigDecimal("0.650")).rateOn(june));
    }

    @Test
    void aGoalIsOnTrackByPaceOrByScheduledPay() {
        GoalProgress thursday = progress("600", "412.50", "84.00", "78.40", LocalDate.of(2026, 9, 10));
        assertEquals(new BigDecimal("68.8"), thursday.percent());
        assertEquals(new BigDecimal("103.50"), thursday.remaining());
        assertEquals(2, thursday.blocksToGo());
        assertTrue(thursday.onTrack());
        assertEquals(MONDAY, thursday.periodStart());
        assertEquals(SUNDAY, thursday.periodEnd());

        assertTrue(progress("600", "100.00", "0", "80", MONDAY).onTrack());
        assertFalse(progress("600", "300.00", "0", "80", LocalDate.of(2026, 9, 12)).onTrack());
        GoalProgress topped = progress("600", "500.00", "100.00", "80", SUNDAY);
        assertTrue(topped.onTrack());
        assertEquals(new BigDecimal("0.00"), topped.remaining());
        assertEquals(0, topped.blocksToGo());
    }

    @Test
    void blocksToGoRoundsUpAndPassingTheGoalShowsOverOneHundredPercent() {
        assertEquals(2, progress("600", "443.20", "0", "78.40", MONDAY).blocksToGo());
        assertEquals(3, progress("600", "443.19", "0", "78.40", MONDAY).blocksToGo());
        assertEquals(0, progress("600", "0", "0", null, MONDAY).blocksToGo());
        assertNull(progress("600", "0", "0", null, MONDAY).averageBlockPay());
        assertEquals(new BigDecimal("100.8"), progress("600", "604.50", "0", "80", SUNDAY).percent());
        assertNull(GoalProgress.of(null, BigDecimal.TEN, BigDecimal.ZERO, null, null, MONDAY, SUNDAY, MONDAY));
    }

    @Test
    void payPeriodsFollowThePayoutDaysAndTheLag() {
        assertEquals(new PayPeriods.Period(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14)),
                PayPeriods.periodFor(LocalDate.of(2026, 9, 14), TUESDAY_FRIDAY, 1));
        assertEquals(new PayPeriods.Period(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 17)),
                PayPeriods.periodFor(LocalDate.of(2026, 9, 15), TUESDAY_FRIDAY, 1));
        assertEquals(new PayPeriods.Period(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18)),
                PayPeriods.periodFor(LocalDate.of(2026, 9, 18), EnumSet.of(DayOfWeek.FRIDAY), 0));
        assertEquals(new PayPeriods.Period(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 29), LocalDate.of(2026, 12, 31)),
                PayPeriods.periodFor(LocalDate.of(2026, 12, 31), TUESDAY_FRIDAY, 1));
        assertEquals(LocalDate.of(2026, 9, 18), PayPeriods.nextPayout(LocalDate.of(2026, 9, 16), TUESDAY_FRIDAY, 1).payoutDate());
    }

    @Test
    void consecutivePayPeriodsLeaveNoGaps() {
        PayPeriods.Period period = PayPeriods.periodFor(LocalDate.of(2026, 9, 28), TUESDAY_FRIDAY, 2);
        for (int index = 0; index < 20; index++) {
            PayPeriods.Period earlier = PayPeriods.previous(period, TUESDAY_FRIDAY, 2);
            assertEquals(period.from(), earlier.to().plusDays(1));
            period = earlier;
        }
    }

    @Test
    void payoutDaysAreReadForgivinglyAndStoredMondayFirst() {
        assertEquals(TUESDAY_FRIDAY, PayPeriods.parseDays("friday, ,bogus,TUESDAY"));
        assertEquals(TUESDAY_FRIDAY, PayPeriods.parseDays(null));
        assertEquals(TUESDAY_FRIDAY, PayPeriods.parseDays("nonsense"));
        assertEquals("MONDAY,FRIDAY", PayPeriods.formatDays(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)));
        assertEquals("", PayPeriods.formatDays(EnumSet.noneOf(DayOfWeek.class)));
    }

    private static GoalProgress progress(String goal, String earned, String planned, String averageBlock,
            LocalDate today) {
        return GoalProgress.of(new BigDecimal(goal), new BigDecimal(earned), new BigDecimal(planned),
                averageBlock == null ? null : new BigDecimal(averageBlock), 240, MONDAY, SUNDAY, today);
    }
}
