package com.angel.flexbuddy.dto;

/** Where a payout stands against what its blocks earned. */
public enum PayoutStatus {
    /** The payout date has not come yet. */
    UPCOMING,
    /** The payout date has passed and nothing was recorded for it. */
    UNCHECKED,
    /** What landed is within a cent of what the blocks earned. */
    MATCHED,
    /** Less landed than the blocks earned. */
    SHORT,
    /** More landed than the blocks earned, usually tips from earlier blocks arriving late. */
    OVER
}
