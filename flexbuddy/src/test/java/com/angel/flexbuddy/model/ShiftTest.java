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

    private Shift shift(LocalTime start, LocalTime end, String base, String tips) {
        return new Shift(1L, "VEA7", LocalDate.of(2026, 9, 6), start, end,
                new BigDecimal(base), new BigDecimal(tips));
    }
}
