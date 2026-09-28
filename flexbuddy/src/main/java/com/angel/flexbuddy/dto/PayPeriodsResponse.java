package com.angel.flexbuddy.dto;

import java.util.List;

/**
 * The period covering today's blocks and the ones before it, newest first, plus the next payout to arrive, which is
 * the previous period on the days between a period's end and its payout.
 */
public record PayPeriodsResponse(PayPeriodResponse nextPayout, List<PayPeriodResponse> periods) {
}
