package com.angel.flexbuddy.importcore.export;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the expenses CSV, as plain values for one of the signed-in account's expenses. {@code category} is the
 * category name ({@code FUEL}, {@code TOLL}, {@code PARKING}, {@code MAINTENANCE} or {@code OTHER}); {@code blockId}
 * and {@code station} are those of the linked block, or null. Any value may be null.
 */
public record ExpenseCsvRow(LocalDate date, String category, BigDecimal amount, String note, Long blockId,
        String station) {
}
