package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.exception.InvalidFilterException;

public enum ShiftSort {
    DATE, STATION, BASE_PAY, TIPS, TOTAL_PAY, TIME_WORKED, HOURLY_RATE;

    public static ShiftSort parse(String value) {
        if (value == null || value.isBlank()) return DATE;
        String normalized = value.trim().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidFilterException("Unknown sort: " + value);
        }
    }
}
