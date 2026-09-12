package com.angel.flexbuddy.dto;

import java.time.LocalDate;

import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.model.ExpenseCategory;

public record ExpenseFilter(LocalDate from, LocalDate to, String station, String query,
        ExpenseCategory category, Long shiftId) {
    public ExpenseFilter {
        station = normalize(station);
        query = normalize(query);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidFilterException("The start date must be on or before the end date.");
        }
    }

    public static ExpenseFilter of(LocalDate from, LocalDate to, String station, String query,
            String category, Long shiftId) {
        return new ExpenseFilter(from, to, station, query, ExpenseCategory.parse(category), shiftId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
