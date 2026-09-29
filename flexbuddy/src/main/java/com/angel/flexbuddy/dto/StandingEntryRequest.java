package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.model.StandingLevel;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The standing to log for a day; a missing or unknown level is rejected. */
public record StandingEntryRequest(
        @NotNull StandingLevel level,
        @Size(max = 255) String note
) {
}
