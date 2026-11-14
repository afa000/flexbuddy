package com.angel.flexbuddy.api;

import static com.angel.flexbuddy.api.ApiValues.code;
import static com.angel.flexbuddy.api.ApiValues.instant;
import static com.angel.flexbuddy.api.ApiValues.money;

import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.service.ExpenseService;

/**
 * An expense as the API returns it (NA-06). Only what is stored on the expense itself: whether it counts toward net,
 * its station and whether its block was deleted all depend on the block, which a change-only sync would not send
 * again with the expense. The phone works those out from its block cache; see docs/api-v1.md.
 */
public record ApiExpense(
        long id,
        String date,
        String category,
        String amount,
        String note,
        Long shiftId,
        String createdAt,
        String updatedAt
) {
    static ApiExpense of(Expense expense) {
        return new ApiExpense(expense.getId(), ApiValues.date(expense.getDate()), code(expense.getCategory()),
                money(expense.getAmount()), expense.getNote(), ExpenseService.linkedShiftId(expense),
                instant(expense.getCreatedAt()), instant(expense.getUpdatedAt()));
    }
}
