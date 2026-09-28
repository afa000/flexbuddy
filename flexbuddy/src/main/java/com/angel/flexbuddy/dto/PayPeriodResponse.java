package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One payout: the dates whose blocks it covers, what those blocks earned, and what is still scheduled in them. */
public record PayPeriodResponse(
        LocalDate payoutDate,
        LocalDate from,
        LocalDate to,
        int blocks,
        BigDecimal earned,
        int scheduledBlocks,
        BigDecimal scheduledPay
) {
}
