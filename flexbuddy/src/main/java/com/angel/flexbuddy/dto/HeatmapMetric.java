package com.angel.flexbuddy.dto;

import java.util.Locale;

import com.angel.flexbuddy.exception.InvalidFilterException;

/** What each heatmap cell shows. */
public enum HeatmapMetric {
    NET_HOURLY, GROSS_HOURLY, SHIFTS, AVERAGE_PAY;

    public static HeatmapMetric parse(String value) {
        if (value == null || value.isBlank()) return NET_HOURLY;
        try {
            return valueOf(value.trim().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidFilterException("error.filter.unknownHeatmapMetric", value);
        }
    }
}
