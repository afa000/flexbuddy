package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One expense that counts, as plain values. The consumer leaves out expenses that wait for a scheduled block. */
public record Cost(CostCategory category, BigDecimal amount, LocalDate date) {
}
