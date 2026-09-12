package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.angel.flexbuddy.model.ExpenseCategory;

public record ExpenseSummaryResponse(BigDecimal total, int count, Map<ExpenseCategory, BigDecimal> byCategory) {}
