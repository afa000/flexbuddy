package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Worked blocks grouped by weekday and start-time band. Scale holds the upper bound of each of up to five colour
 * steps, from the values of cells that are not sparse; best is null when every cell is sparse.
 */
public record HeatmapResponse(
        HeatmapMetric metric,
        List<String> bands,
        List<HeatmapCell> cells,
        HeatmapCell best,
        List<BigDecimal> scale,
        int totalShifts
) {
}
