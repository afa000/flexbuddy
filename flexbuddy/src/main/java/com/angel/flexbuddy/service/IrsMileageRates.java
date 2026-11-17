package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The IRS standard mileage rates for business use. The dated table lives in the shared library
 * ({@link com.angel.flexbuddy.importcore.figures.IrsMileageRates}), so the app uses the same rates; add a new rate
 * there.
 */
public final class IrsMileageRates {

    private IrsMileageRates() {
    }

    /** The rate in effect on {@code date}: the latest entry on or before it. */
    public static BigDecimal rateOn(LocalDate date) {
        return com.angel.flexbuddy.importcore.figures.IrsMileageRates.rateOn(date);
    }

    /** The date the rate in effect on {@code date} took effect (the first entry's date for earlier dates). */
    public static LocalDate sinceOn(LocalDate date) {
        return com.angel.flexbuddy.importcore.figures.IrsMileageRates.sinceOn(date);
    }

    /** The distinct rates in effect on any day from {@code from} to {@code to}, oldest first. */
    public static List<BigDecimal> ratesBetween(LocalDate from, LocalDate to) {
        return com.angel.flexbuddy.importcore.figures.IrsMileageRates.ratesBetween(from, to);
    }
}
