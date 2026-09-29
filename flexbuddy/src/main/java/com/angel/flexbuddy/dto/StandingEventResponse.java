package com.angel.flexbuddy.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record StandingEventResponse(LocalDate date, LocalTime startTime, String station, StandingEventKind kind) {
}
