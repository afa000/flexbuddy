package com.angel.flexbuddy.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class ShiftTest {

    @Test
    void getTotalPay_addsBasePayAndTips() {
        Shift shift = new Shift(
                1L,
                "VEA7",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal("120.00"),
                new BigDecimal("35.50")
        );

        BigDecimal result = shift.getTotalPay();

        assertThat(result).isEqualByComparingTo("155.50");
    }

    @Test
    void getTimeWorked_returnsMinutesBetweenStartAndEnd() {
        Shift shift = new Shift(
                1L,
                "VEA7",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(10, 15),
                LocalTime.of(14, 45),
                new BigDecimal("80.00"),
                new BigDecimal("10.00")
        );

        int result = shift.getTimeWorked();

        assertThat(result).isEqualTo(270);
    }

    @Test
    void getTimeWorked_treatsAnEarlierEndTimeAsOvernight() {
        Shift shift = shift(LocalTime.of(22, 30), LocalTime.of(2, 0), "140.00", "0.00");

        assertThat(shift.getTimeWorked()).isEqualTo(210);
    }

    @Test
    void getHourlyRate_usesTotalPayAndRoundsToCents() {
        Shift shift = shift(LocalTime.of(9, 0), LocalTime.of(14, 30), "100.00", "20.00");

        assertThat(shift.getHourlyRate()).isEqualByComparingTo("21.82");
    }

    @Test
    void getHourlyRate_returnsZeroForZeroMinutes() {
        Shift shift = shift(LocalTime.NOON, LocalTime.NOON, "100.00", "20.00");

        assertThat(shift.getHourlyRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void getTimeWorked_returnsZeroWhenALegacyRowIsMissingATime() {
        assertThat(shift(null, LocalTime.of(17, 0), "100.00", "0.00").getTimeWorked()).isZero();
        assertThat(shift(LocalTime.of(9, 0), null, "100.00", "0.00").getTimeWorked()).isZero();
    }

    private Shift shift(LocalTime start, LocalTime end, String base, String tips) {
        return new Shift(1L, "VEA7", LocalDate.of(2026, 9, 6), start, end,
                new BigDecimal(base), new BigDecimal(tips));
    }

    @Test
    void accountingRulesFollowTheShiftStatus() {
        Shift scheduled = withStatus(ShiftStatus.SCHEDULED, "84.00", "0.00", null);
        Shift completed = withStatus(ShiftStatus.COMPLETED, "84.00", "12.00", "30.0");
        Shift cancelled = withStatus(ShiftStatus.CANCELLED, "18.00", "0.00", "22.0");
        Shift forfeited = withStatus(ShiftStatus.FORFEITED, "0.00", "0.00", "10.0");

        assertThat(scheduled.countsTowardEarnings()).isFalse();
        assertThat(scheduled.getEarnedPay()).isEqualByComparingTo("0");
        assertThat(scheduled.getWorkedMinutes()).isZero();
        assertThat(scheduled.getCountedMiles()).isNull();

        assertThat(completed.getEarnedPay()).isEqualByComparingTo("96.00");
        assertThat(completed.getWorkedMinutes()).isEqualTo(240);
        assertThat(completed.getCountedMiles()).isEqualByComparingTo("30.0");

        assertThat(cancelled.getEarnedPay()).isEqualByComparingTo("18.00");
        assertThat(cancelled.countsTowardHours()).isFalse();
        assertThat(cancelled.getWorkedMinutes()).isZero();
        assertThat(cancelled.getCountedMiles()).isEqualByComparingTo("22.0");

        assertThat(forfeited.countsTowardEarnings()).isFalse();
        assertThat(forfeited.countsTowardHours()).isFalse();
        assertThat(forfeited.countsTowardMiles()).isTrue();
    }

    @Test
    void onlyCompletedShiftsEarnTips() {
        Shift cancelled = withStatus(ShiftStatus.CANCELLED, "18.00", "5.00", null);

        assertThat(cancelled.getEarnedTips()).isEqualByComparingTo("0");
        assertThat(cancelled.getEarnedPay()).isEqualByComparingTo("18.00");
    }

    @Test
    void theEndDateTimeRollsOverMidnight() {
        Shift overnight = shift(LocalTime.of(22, 30), LocalTime.of(2, 0), "140.00", "0.00");

        assertThat(overnight.getStartDateTime()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 6, 22, 30));
        assertThat(overnight.getEndDateTime()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 7, 2, 0));
    }

    private Shift withStatus(ShiftStatus status, String base, String tips, String miles) {
        Shift shift = new Shift(1L, "VEA7", LocalDate.of(2026, 9, 6), LocalTime.of(15, 15), LocalTime.of(19, 15),
                new BigDecimal(base), new BigDecimal(tips));
        shift.setMiles(miles == null ? null : new BigDecimal(miles));
        shift.setStatus(status);
        return shift;
    }
}
