package com.angel.flexbuddy.dto;

import java.time.LocalDate;

import com.angel.flexbuddy.model.StandingLevel;

public record StandingEntryResponse(LocalDate recordedOn, StandingLevel level, String note) {
}
