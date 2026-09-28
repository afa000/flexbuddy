package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

/**
 * Worked blocks that started on one weekday (ISO, 1 is Monday) in one time band. A sparse cell has fewer than two
 * blocks, so it is shown but left out of the colour scale and never named best.
 */
public record HeatmapCell(int weekday, int band, int shifts, int minutes, BigDecimal value, boolean sparse) {
}
