package com.angel.flexbuddy.dto;

import java.io.Serializable;
import java.time.LocalDate;

/** What landed for one payout, as recorded by the driver. */
public record BackupPayout(LocalDate payoutDate, String amount, String note) implements Serializable {
}
