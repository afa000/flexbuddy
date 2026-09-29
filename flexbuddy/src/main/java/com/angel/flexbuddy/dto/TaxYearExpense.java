package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.angel.flexbuddy.model.ExpenseCategory;

/** One expense counted in a tax year. The station is the linked block's, or null when it is not linked to one. */
public record TaxYearExpense(LocalDate date, ExpenseCategory category, BigDecimal amount, String station, String note) {
}
