package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.exception.InvalidFilterException;

public enum GroupBy {
    STATION, WEEK, MONTH, YEAR;

    public static GroupBy parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFilterException("error.filter.groupByRequired");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new InvalidFilterException("error.filter.unknownGroupBy", value);
        }
    }
}
