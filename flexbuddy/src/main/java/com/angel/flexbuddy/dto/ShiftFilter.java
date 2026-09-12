package com.angel.flexbuddy.dto;

import java.time.LocalDate;

import com.angel.flexbuddy.exception.InvalidFilterException;

public record ShiftFilter(
        LocalDate from,
        LocalDate to,
        String station,
        String query,
        ShiftSort sort,
        SortDirection direction
) {
    public ShiftFilter {
        station = normalize(station);
        query = normalize(query);
        sort = sort == null ? ShiftSort.DATE : sort;
        direction = direction == null ? SortDirection.DESC : direction;
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidFilterException("The start date must be on or before the end date.");
        }
    }

    public static ShiftFilter of(LocalDate from, LocalDate to, String station, String query, String sort, String direction) {
        return new ShiftFilter(from, to, station, query, ShiftSort.parse(sort), SortDirection.parse(direction));
    }

    public static ShiftFilter report(LocalDate from, LocalDate to, String station, String query) {
        return new ShiftFilter(from, to, station, query, ShiftSort.DATE, SortDirection.DESC);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
