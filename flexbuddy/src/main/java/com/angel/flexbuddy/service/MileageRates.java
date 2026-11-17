package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Where a block's mileage rate comes from: the account's own rate when it has one (the same for every date),
 * otherwise the IRS rate in effect on the block's date. The rule is the shared library's.
 */
public record MileageRates(BigDecimal customRate) {
    private static final MileageRates IRS = new MileageRates(null);

    public static MileageRates irs() {
        return IRS;
    }

    public static MileageRates fixed(BigDecimal rate) {
        return new MileageRates(rate);
    }

    /** The one rate in effect for every day from {@code from} to {@code to}, or null when it changes within them. */
    public BigDecimal singleRate(LocalDate from, LocalDate to) {
        return figures().singleRate(from, to);
    }

    public BigDecimal rateOn(LocalDate date) {
        return figures().rateOn(date);
    }

    /** The same rates as the shared library's type. */
    public com.angel.flexbuddy.importcore.figures.MileageRates figures() {
        return com.angel.flexbuddy.importcore.figures.MileageRates.fixed(customRate);
    }
}
