package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.angel.flexbuddy.model.VehicleCostMethod;

/**
 * Everything on the printable tax year summary: the twelve months and the year total, the estimated-payment periods
 * and payments recorded from the tax summary, and every expense the monthly totals count, oldest first. Each expense
 * category's listed amounts add up to the year total's column for it. Every tax figure is an estimate.
 */
public record TaxYearReport(
        int year,
        LocalDate generatedOn,
        String displayName,
        VehicleCostMethod vehicleCostMethod,
        BigDecimal mileageRate,
        List<TaxYearRow> months,
        TaxYearRow total,
        TaxSummaryResponse summary,
        List<TaxYearExpense> expenses
) {
    public TaxYearReport {
        months = months == null ? List.of() : List.copyOf(months);
        expenses = expenses == null ? List.of() : List.copyOf(expenses);
    }
}
