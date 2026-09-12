package com.angel.flexbuddy.model;

import java.util.Locale;

import com.angel.flexbuddy.exception.InvalidFilterException;

public enum ExpenseCategory {
    FUEL, TOLL, PARKING, MAINTENANCE, OTHER;

    public static ExpenseCategory parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidFilterException("Unknown expense category: " + value);
        }
    }
}
