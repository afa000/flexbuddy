package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * What happened on a block beyond its schedule and pay. When a request carries this object, every value in it
 * replaces the saved one, so a null clears it; when the object is left out, the saved details are kept.
 */
public record BlockDetailsRequest(
        LocalTime actualStart,
        LocalTime actualEnd,
        @PositiveOrZero @Digits(integer = 8, fraction = 1) BigDecimal odometerStart,
        @PositiveOrZero @Digits(integer = 8, fraction = 1) BigDecimal odometerEnd
) {

    public static final BlockDetailsRequest EMPTY = new BlockDetailsRequest(null, null, null, null);
}
