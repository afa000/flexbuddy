package com.angel.flexbuddy.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.importcore.figures.PayPeriods;

/**
 * Works out pay periods from a payout schedule: the weekdays money arrives and a lag in days. A block is paid on the
 * first payout day at least the lag after it, so with Tuesday and Friday payouts and a lag of 1, a Monday block is
 * paid on Tuesday and a Tuesday block on Friday. The rule is the shared library's {@link PayPeriods}.
 */
@Component
public class PayPeriodCalculator {

    public record Period(LocalDate payoutDate, LocalDate from, LocalDate to) {
        public boolean contains(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }

        static Period of(PayPeriods.Period period) {
            return new Period(period.payoutDate(), period.from(), period.to());
        }

        PayPeriods.Period figures() {
            return new PayPeriods.Period(payoutDate, from, to);
        }
    }

    /** The period whose payout includes blocks worked on the given date. */
    public Period periodFor(LocalDate date, Set<DayOfWeek> payoutDays, int lagDays) {
        return Period.of(PayPeriods.periodFor(date, payoutDays, lagDays));
    }

    /** The period paid out on the first payout day on or after the given date. */
    public Period nextPayout(LocalDate date, Set<DayOfWeek> payoutDays, int lagDays) {
        return Period.of(PayPeriods.nextPayout(date, payoutDays, lagDays));
    }

    /** The period paid out just before the given one. */
    public Period previous(Period period, Set<DayOfWeek> payoutDays, int lagDays) {
        return Period.of(PayPeriods.previous(period.figures(), payoutDays, lagDays));
    }

    /** Reads the stored comma-separated weekdays, falling back to Tuesday and Friday if none can be read. */
    public static Set<DayOfWeek> parseDays(String value) {
        return PayPeriods.parseDays(value);
    }

    public static String formatDays(Set<DayOfWeek> days) {
        return PayPeriods.formatDays(days);
    }
}
