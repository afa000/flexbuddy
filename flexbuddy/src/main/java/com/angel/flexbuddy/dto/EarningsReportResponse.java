package com.angel.flexbuddy.dto;

import java.time.LocalDate;
import java.util.List;

public record EarningsReportResponse(
        String groupBy,
        LocalDate from,
        LocalDate to,
        List<EarningsBucket> buckets,
        EarningsTotals totals
) {}
