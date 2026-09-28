package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A tax year's estimated reserve: the driver's own percentage applied to net earnings, less the payments they
 * recorded. Every amount is an estimate. The reserve, remaining, and set-aside figures are null until a percentage
 * is chosen; nextDueDate is null once the year's last due date has passed.
 */
public record TaxSummaryResponse(
        int year,
        BigDecimal percent,
        BigDecimal netYearToDate,
        BigDecimal reserveToDate,
        BigDecimal paid,
        BigDecimal remaining,
        BigDecimal thisWeekNet,
        BigDecimal thisWeekSetAside,
        LocalDate nextDueDate,
        List<TaxQuarterResponse> quarters,
        List<TaxPaymentResponse> payments
) {
}
