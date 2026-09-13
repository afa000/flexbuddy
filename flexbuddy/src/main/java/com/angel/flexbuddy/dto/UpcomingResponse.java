package com.angel.flexbuddy.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UpcomingResponse(
        LocalDateTime now,
        String timeZone,
        ShiftResponse next,
        List<UpcomingDay> days,
        List<UpcomingWeek> weeks,
        List<ShiftConflict> conflicts,
        List<ShiftResponse> needsConfirmation
) {}
