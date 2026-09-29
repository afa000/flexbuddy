package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One payout: the dates whose blocks it covers, what those blocks earned, and what is still scheduled in them. Once
 * the driver records what landed, received and note hold it and difference is received minus earned; all three are
 * null until then.
 */
public record PayPeriodResponse(
        LocalDate payoutDate,
        LocalDate from,
        LocalDate to,
        int blocks,
        BigDecimal earned,
        int scheduledBlocks,
        BigDecimal scheduledPay,
        BigDecimal received,
        BigDecimal difference,
        String note,
        PayoutStatus status
) {
}
