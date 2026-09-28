package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One estimated-payment period: the dates it covers, its due date, what it netted, and what was paid for it. */
public record TaxQuarterResponse(
        int quarter,
        LocalDate from,
        LocalDate to,
        LocalDate dueDate,
        BigDecimal net,
        BigDecimal setAside,
        BigDecimal paid
) {
}
