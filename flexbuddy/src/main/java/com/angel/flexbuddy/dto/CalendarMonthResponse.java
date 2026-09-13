package com.angel.flexbuddy.dto;

import java.util.List;

public record CalendarMonthResponse(String month, List<CalendarDaySummary> days) {}
