package com.angel.flexbuddy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import com.angel.flexbuddy.dto.ExpenseRequest;
import com.angel.flexbuddy.model.ExpenseCategory;

/** The body of an expense create or edit (NA-06); categories are lower-case codes as the API returns them. */
public record ApiExpenseInput(
        LocalDate date,
        String category,
        BigDecimal amount,
        String note,
        Long shiftId,
        Instant expectedUpdatedAt
) {
    /** The website's request, so the same validation and rules apply. */
    ExpenseRequest request() {
        ExpenseCategory parsed = null;
        if (category != null) {
            try {
                parsed = ExpenseCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ApiWriteReceipts.InvalidWrite(Map.of("category", "invalid"));
            }
        }
        return new ExpenseRequest(date, parsed, amount, note, shiftId);
    }
}
