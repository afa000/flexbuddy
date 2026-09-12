package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.exception.InvalidFilterException;

public enum SortDirection {
    ASC, DESC;

    public static SortDirection parse(String value) {
        if (value == null || value.isBlank()) return DESC;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new InvalidFilterException("Unknown sort direction: " + value);
        }
    }
}
