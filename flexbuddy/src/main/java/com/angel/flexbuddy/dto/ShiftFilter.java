package com.angel.flexbuddy.dto;

import java.time.LocalDate;
import java.util.Set;

import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.model.ShiftStatus;

public record ShiftFilter(
        LocalDate from,
        LocalDate to,
        String station,
        String query,
        ShiftSort sort,
        SortDirection direction,
        Set<ShiftStatus> statuses
) {
    public ShiftFilter {
        station = normalize(station);
        query = normalize(query);
        sort = sort == null ? ShiftSort.DATE : sort;
        direction = direction == null ? SortDirection.DESC : direction;
        statuses = statuses == null || statuses.isEmpty() ? ShiftStatus.HISTORY : Set.copyOf(statuses);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidFilterException("The start date must be on or before the end date.");
        }
    }

    /** History: everything that has already happened. */
    public static ShiftFilter of(LocalDate from, LocalDate to, String station, String query, String sort, String direction) {
        return of(from, to, station, query, sort, direction, null);
    }

    public static ShiftFilter of(LocalDate from, LocalDate to, String station, String query, String sort,
            String direction, String status) {
        return new ShiftFilter(from, to, station, query, ShiftSort.parse(sort), SortDirection.parse(direction),
                ShiftStatus.parseSet(status, ShiftStatus.HISTORY));
    }

    /** Statistics, reports, and CSV: only rows that can carry earnings. */
    public static ShiftFilter report(LocalDate from, LocalDate to, String station, String query) {
        return report(from, to, station, query, null);
    }

    public static ShiftFilter report(LocalDate from, LocalDate to, String station, String query, String status) {
        return new ShiftFilter(from, to, station, query, ShiftSort.DATE, SortDirection.DESC,
                ShiftStatus.parseSet(status, ShiftStatus.EARNINGS));
    }

    public ShiftFilter withStatuses(Set<ShiftStatus> replacement) {
        return new ShiftFilter(from, to, station, query, sort, direction, replacement);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
