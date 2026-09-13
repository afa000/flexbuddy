package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpcomingDay(LocalDate date, List<ShiftResponse> shifts, int plannedMinutes, BigDecimal expectedPay) {}
