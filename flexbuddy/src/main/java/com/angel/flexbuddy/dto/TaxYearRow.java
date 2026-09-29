package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import com.angel.flexbuddy.model.VehicleCostMethod;

/**
 * One month, or the whole year, of a tax year summary: what the blocks paid, the miles and what they are worth, each
 * expense category, and the net. The year CSV and the printable summary are both written from these rows, so the two
 * cannot drift apart. The label is YYYY-MM for a month and YYYY for the year total.
 */
public record TaxYearRow(
        String label,
        int shifts,
        BigDecimal basePay,
        BigDecimal tips,
        BigDecimal gross,
        BigDecimal miles,
        BigDecimal mileageRate,
        BigDecimal mileageCost,
        BigDecimal fuel,
        BigDecimal tolls,
        BigDecimal parking,
        BigDecimal maintenance,
        BigDecimal other,
        VehicleCostMethod vehicleCostMethod,
        BigDecimal vehicleCost,
        BigDecimal totalDeductions,
        BigDecimal net
) {
}
