package com.angel.flexbuddy.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The API's value conventions (docs/api-v1.md): money as two-decimal strings, local times without seconds. */
final class ApiValues {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ApiValues() {
    }

    /** Money: always two decimals, never a floating-point number. */
    static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** A decimal that isn't money (miles, a rate per mile, a percentage), as written. */
    static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    static String time(LocalTime value) {
        return value == null ? null : value.format(TIME);
    }

    static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    static String code(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
