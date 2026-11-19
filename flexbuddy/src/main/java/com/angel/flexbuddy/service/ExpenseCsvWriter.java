package com.angel.flexbuddy.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.dto.ExpenseResponse;
import com.angel.flexbuddy.importcore.export.ExpenseCsv;
import com.angel.flexbuddy.importcore.export.ExpenseCsvRow;

/** The website's expenses CSV: maps the signed-in driver's responses to rows for the shared {@link ExpenseCsv}. */
@Component
public class ExpenseCsvWriter {

    /** Writes the expenses CSV and closes the stream (see {@link ExpenseCsv}). */
    public void write(List<ExpenseResponse> expenses, OutputStream output) throws IOException {
        List<ExpenseCsvRow> rows = new ArrayList<>(expenses.size());
        for (ExpenseResponse expense : expenses) rows.add(row(expense));
        ExpenseCsv.write(rows, output);
    }

    static ExpenseCsvRow row(ExpenseResponse expense) {
        return new ExpenseCsvRow(expense.date(), expense.category() == null ? null : expense.category().name(),
                expense.amount(), expense.note(), expense.shiftId(), expense.station());
    }
}
