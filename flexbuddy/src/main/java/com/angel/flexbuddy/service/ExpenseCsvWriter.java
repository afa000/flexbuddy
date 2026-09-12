package com.angel.flexbuddy.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.dto.ExpenseResponse;

@Component
public class ExpenseCsvWriter {
    public void write(List<ExpenseResponse> expenses, OutputStream output) throws IOException {
        try (var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write("Date,Category,Amount,Note,Shift ID,Station\r\n");
            for (ExpenseResponse expense : expenses) {
                writer.write(String.join(",", csv(expense.date()), csv(expense.category()), csv(expense.amount()),
                        csv(expense.note()), csv(expense.shiftId()), csv(expense.station())) + "\r\n");
            }
        }
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.matches("^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
