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
}
