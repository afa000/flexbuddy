package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Where a block's mileage rate comes from: the account's own rate when it has one (the same for every date),
 * otherwise the IRS rate in effect on the block's date.
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
        if (customRate != null) return customRate;
        var rates = IrsMileageRates.ratesBetween(from, to);
        return rates.size() == 1 ? rates.get(0) : null;
    }

    public BigDecimal rateOn(LocalDate date) {
        return customRate != null ? customRate : IrsMileageRates.rateOn(date);
    }
}
