package com.angel.flexbuddy.importcore.export;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One row of the blocks CSV, as plain values the consumer has already computed (with the shared figures) for one of
 * the signed-in account's blocks. {@link ShiftCsv} only formats them. Any value may be null except the primitives;
 * {@code status} is the status name ({@code SCHEDULED}, {@code COMPLETED}, {@code CANCELLED} or {@code FORFEITED}),
 * and the detail values are null when the block has no recorded details.
 */
public record ShiftCsvRow(
        Long id,
        LocalDate date,
        String station,
        LocalTime startTime,
        LocalTime endTime,
        int minutesWorked,
        BigDecimal basePay,
        BigDecimal tips,
        BigDecimal totalPay,
        BigDecimal hourlyRate,
        BigDecimal miles,
        BigDecimal mileageCost,
        BigDecimal linkedExpenses,
        BigDecimal netPay,
        BigDecimal netHourlyRate,
        BigDecimal earningsPerMile,
        Instant createdAt,
        Instant updatedAt,
        String status,
        LocalTime actualStart,
        LocalTime actualEnd,
        Integer actualMinutes,
        BigDecimal actualHourlyRate,
        BigDecimal odometerStart,
        BigDecimal odometerEnd,
        Integer stops,
        Integer packages,
        Integer returns,
        BigDecimal minutesPerStop,
        boolean lateForfeit
) {
}
