package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The IRS standard mileage rates for business use, by the date each took effect.
 *
 * <p>When the IRS announces a new rate, or changes one mid-year, add a line to {@link #TABLE} (dates in ascending
 * order) and a test for it. Dates before the first entry use the first entry's rate, and a missing date the
 * newest.
 */
public final class IrsMileageRates {
    public record Entry(LocalDate from, BigDecimal rate) {
    }

    static final List<Entry> TABLE = List.of(
            new Entry(LocalDate.of(2024, 1, 1), new BigDecimal("0.670")),
            new Entry(LocalDate.of(2025, 1, 1), new BigDecimal("0.700")),
            new Entry(LocalDate.of(2026, 1, 1), new BigDecimal("0.725")),
            new Entry(LocalDate.of(2026, 7, 1), new BigDecimal("0.760")));

    private IrsMileageRates() {
    }

    /** The rate in effect on {@code date}: the latest entry on or before it. */
    public static BigDecimal rateOn(LocalDate date) {
        return entryOn(date).rate();
    }

    /** The date the rate in effect on {@code date} took effect (the first entry's date for earlier dates). */
    public static LocalDate sinceOn(LocalDate date) {
        return entryOn(date).from();
    }

    /** The distinct rates in effect on any day from {@code from} to {@code to}, oldest first. */
    public static List<BigDecimal> ratesBetween(LocalDate from, LocalDate to) {
        List<BigDecimal> rates = new java.util.ArrayList<>();
        rates.add(rateOn(from));
        for (Entry entry : TABLE) {
            if (entry.from().isAfter(from) && !entry.from().isAfter(to) && !rates.contains(entry.rate())) {
                rates.add(entry.rate());
            }
        }
        return rates;
    }

    private static Entry entryOn(LocalDate date) {
        if (date == null) return TABLE.get(TABLE.size() - 1);
        Entry found = TABLE.get(0);
        for (Entry entry : TABLE) {
            if (!entry.from().isAfter(date)) found = entry;
        }
        return found;
    }
}
