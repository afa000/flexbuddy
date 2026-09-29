package com.angel.flexbuddy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The last stretch of days, from and to inclusive. Entries start with the one in force on the first day, if any,
 * keeping its real date, then the ones logged inside the window. Current is the latest entry overall, which can be
 * older than the window, and is null when nothing was ever logged.
 */
public record StandingResponse(
        LocalDate from,
        LocalDate to,
        StandingEntryResponse current,
        List<StandingEntryResponse> entries,
        List<StandingEventResponse> events
) {
    public StandingResponse {
        entries = entries == null ? List.of() : List.copyOf(entries);
        events = events == null ? List.of() : List.copyOf(events);
    }
}
