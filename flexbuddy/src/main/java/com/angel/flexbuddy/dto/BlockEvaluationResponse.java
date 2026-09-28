package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

/**
 * What an offered block is likely to net, next to what the driver usually nets. The usual rates, verdict, and
 * difference are null when there is no history to compare against, and estimatedMiles is null when no miles
 * were logged in the sample. The usual base rate is the most common base pay per hour in the sample, rounded to the
 * dollar, and surgePay is how much of the offer is above it; both are null without history.
 */
public record BlockEvaluationResponse(
        Basis basis,
        int sampleSize,
        String station,
        BigDecimal offeredHourly,
        BigDecimal grossHourly,
        BigDecimal estimatedTips,
        BigDecimal estimatedMiles,
        BigDecimal estimatedVehicleCost,
        BigDecimal estimatedOtherExpenses,
        BigDecimal estimatedNet,
        BigDecimal estimatedNetHourly,
        BigDecimal usualNetHourly,
        BigDecimal usualGrossHourly,
        Verdict verdict,
        BigDecimal differencePercent,
        BigDecimal usualBaseHourly,
        BigDecimal surgePay
) {
    public enum Basis { STATION_90_DAYS, STATION_ALL_TIME, ACCOUNT }

    public enum Verdict { BELOW_USUAL, ABOUT_USUAL, ABOVE_USUAL }
}
