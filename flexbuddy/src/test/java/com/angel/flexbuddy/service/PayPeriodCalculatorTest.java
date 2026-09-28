package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.service.PayPeriodCalculator.Period;

class PayPeriodCalculatorTest {

    private static final Set<DayOfWeek> TUESDAY_FRIDAY = EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);

    private final PayPeriodCalculator calculator = new PayPeriodCalculator();

    @Test
    void withTuesdayAndFridayPayoutsAMondayBlockIsPaidTuesdayAndATuesdayBlockFriday() {
        Period monday = calculator.periodFor(LocalDate.of(2026, 9, 14), TUESDAY_FRIDAY, 1);
        Period tuesday = calculator.periodFor(LocalDate.of(2026, 9, 15), TUESDAY_FRIDAY, 1);

        assertThat(monday).isEqualTo(new Period(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14)));
        assertThat(tuesday).isEqualTo(new Period(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 17)));
    }

    @Test
    void consecutivePeriodsLeaveNoGapsAndNoOverlaps() {
        Period period = calculator.periodFor(LocalDate.of(2026, 9, 28), TUESDAY_FRIDAY, 1);
        for (int index = 0; index < 20; index++) {
            Period earlier = calculator.previous(period, TUESDAY_FRIDAY, 1);
            assertThat(earlier.to().plusDays(1)).isEqualTo(period.from());
            period = earlier;
        }
    }

    @Test
    void aSingleWeeklyPayoutWithNoLagCoversTheWeekUpToAndIncludingPayday() {
        Set<DayOfWeek> fridays = EnumSet.of(DayOfWeek.FRIDAY);

        Period friday = calculator.periodFor(LocalDate.of(2026, 9, 18), fridays, 0);
        Period saturday = calculator.periodFor(LocalDate.of(2026, 9, 19), fridays, 0);

        assertThat(friday).isEqualTo(new Period(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18)));
        assertThat(saturday.payoutDate()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void periodsRunAcrossTheEndOfTheYear() {
        Period newYearsEve = calculator.periodFor(LocalDate.of(2026, 12, 31), TUESDAY_FRIDAY, 1);

        assertThat(newYearsEve).isEqualTo(new Period(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 29), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void theNextPayoutOnPaydayIsTodaysPayoutWhileTodaysBlocksBelongToTheOneAfter() {
        LocalDate tuesday = LocalDate.of(2026, 9, 15);

        assertThat(calculator.nextPayout(tuesday, TUESDAY_FRIDAY, 1).payoutDate()).isEqualTo(tuesday);
        assertThat(calculator.periodFor(tuesday, TUESDAY_FRIDAY, 1).payoutDate()).isEqualTo(LocalDate.of(2026, 9, 18));
    }

    @Test
    void storedDaysAreReadForgivinglyAndWrittenInWeekOrder() {
        assertThat(PayPeriodCalculator.parseDays(" friday,TUESDAY,nonsense")).containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);
        assertThat(PayPeriodCalculator.parseDays("")).isEqualTo(TUESDAY_FRIDAY);
        assertThat(PayPeriodCalculator.formatDays(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))).isEqualTo("MONDAY,FRIDAY");
    }
}
