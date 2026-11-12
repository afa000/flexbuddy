package com.angel.flexbuddy.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;

/**
 * For tests that mock ExpenseService: its block lookups answer from the Shift objects the test linked, deleted ones
 * included, and countable applies the real rule (only an expense on a live scheduled block waits).
 */
final class ExpenseLinkStubs {
    private ExpenseLinkStubs() {}

    static void readLinksFromEntities(ExpenseService expenseService) {
        lenient().when(expenseService.linkedShifts(any(), any()))
                .thenAnswer(invocation -> links(invocation.getArgument(1)));
        lenient().when(expenseService.countable(any(), any())).thenAnswer(invocation -> {
            List<Expense> expenses = invocation.getArgument(1);
            Map<Long, ExpenseService.LinkedShift> links = links(expenses);
            return expenses.stream().filter(expense -> {
                ExpenseService.LinkedShift link = ExpenseService.linkOf(expense, links);
                return link == null || !link.waiting();
            }).toList();
        });
    }

    private static Map<Long, ExpenseService.LinkedShift> links(java.util.Collection<Expense> expenses) {
        Map<Long, ExpenseService.LinkedShift> links = new HashMap<>();
        for (Expense expense : expenses) {
            Shift shift = expense.getShift();
            if (shift == null) continue;
            links.put(shift.getId(), new ExpenseService.LinkedShift(shift.getId(), shift.getStation(),
                    shift.getStatus() == null ? ShiftStatus.COMPLETED : shift.getStatus(), shift.getDeletedAt() != null));
        }
        return links;
    }
}
