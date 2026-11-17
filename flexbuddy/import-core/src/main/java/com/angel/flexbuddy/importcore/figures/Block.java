package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One of the signed-in account's live blocks, as plain values. The consumer selects the blocks; this type only
 * applies {@link EarningsRules} to them.
 */
public record Block(BlockStatus status, String station, LocalDate date, LocalTime start, LocalTime end,
        BigDecimal basePay, BigDecimal tips, BigDecimal miles) {

    public BlockStatus effectiveStatus() {
        return EarningsRules.effective(status);
    }

    public boolean countsTowardHours() {
        return EarningsRules.countsTowardHours(status);
    }

    public int timeWorked() {
        return EarningsRules.timeWorked(start, end);
    }

    public BigDecimal earnedBasePay() {
        return EarningsRules.earnedBasePay(status, basePay);
    }

    public BigDecimal earnedTips() {
        return EarningsRules.earnedTips(status, tips);
    }

    public BigDecimal earnedPay() {
        return earnedBasePay().add(earnedTips());
    }

    public int workedMinutes() {
        return EarningsRules.workedMinutes(status, start, end);
    }

    public BigDecimal countedMiles() {
        return EarningsRules.countedMiles(status, miles);
    }
}
