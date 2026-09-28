package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * A block's recorded details with the figures derived from them. actualMinutes, actualHourlyRate, and
 * finishedEarlyMinutes are null unless both actual times were recorded on a worked block; minutesPerStop needs
 * stops, and returnsRate, a percentage, needs returns and either packages or stops.
 */
public record BlockDetailsResponse(
        LocalTime actualStart,
        LocalTime actualEnd,
        Integer actualMinutes,
        BigDecimal actualHourlyRate,
        Integer finishedEarlyMinutes,
        BigDecimal odometerStart,
        BigDecimal odometerEnd,
        Integer stops,
        Integer packages,
        Integer returns,
        BigDecimal minutesPerStop,
        BigDecimal returnsRate
) {
    public static final BlockDetailsResponse NONE =
            new BlockDetailsResponse(null, null, null, null, null, null, null, null, null, null, null, null);
}
