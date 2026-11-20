package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The website's rounding for money and rates, stated once: two places, half up, and zero for nothing to divide by. */
final class Rounding {

    private Rounding() {
    }

    /** Two places, half up; a missing amount is zero. */
    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** An amount per hour from minutes, to two places; 0.00 with no minutes. */
    static BigDecimal hourly(BigDecimal value, int minutes) {
        return minutes == 0 ? BigDecimal.ZERO.setScale(2)
                : value.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP);
    }

    /** An amount per item, to two places; 0.00 with no items. */
    static BigDecimal divide(BigDecimal value, int count) {
        return count == 0 ? BigDecimal.ZERO.setScale(2)
                : value.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
