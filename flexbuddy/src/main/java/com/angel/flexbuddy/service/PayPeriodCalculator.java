package com.angel.flexbuddy.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Works out pay periods from a payout schedule: the weekdays money arrives and a lag in days. A block is paid on the
 * first payout day at least the lag after it, so with Tuesday and Friday payouts and a lag of 1, a Monday block is
 * paid on Tuesday and a Tuesday block on Friday. Periods are always computed, never stored, so changing the schedule
 * reshapes every period without touching a shift.
 */
@Component
public class PayPeriodCalculator {

    public record Period(LocalDate payoutDate, LocalDate from, LocalDate to) {
        public boolean contains(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }

    /** The period whose payout includes blocks worked on the given date. */
    public Period periodFor(LocalDate date, Set<DayOfWeek> payoutDays, int lagDays) {
        return period(nextPayoutOnOrAfter(date.plusDays(lagDays), payoutDays), payoutDays, lagDays);
    }

    /** The period paid out on the first payout day on or after the given date. */
    public Period nextPayout(LocalDate date, Set<DayOfWeek> payoutDays, int lagDays) {
        return period(nextPayoutOnOrAfter(date, payoutDays), payoutDays, lagDays);
    }

    /** The period paid out just before the given one. */
    public Period previous(Period period, Set<DayOfWeek> payoutDays, int lagDays) {
        return period(previousPayoutBefore(period.payoutDate(), payoutDays), payoutDays, lagDays);
    }

    private Period period(LocalDate payoutDate, Set<DayOfWeek> payoutDays, int lagDays) {
        LocalDate previousPayout = previousPayoutBefore(payoutDate, payoutDays);
        return new Period(payoutDate, previousPayout.minusDays(lagDays - 1L), payoutDate.minusDays(lagDays));
    }

    private static LocalDate nextPayoutOnOrAfter(LocalDate date, Set<DayOfWeek> payoutDays) {
        LocalDate day = date;
        while (!payoutDays.contains(day.getDayOfWeek())) day = day.plusDays(1);
        return day;
    }

    private static LocalDate previousPayoutBefore(LocalDate date, Set<DayOfWeek> payoutDays) {
        LocalDate day = date.minusDays(1);
        while (!payoutDays.contains(day.getDayOfWeek())) day = day.minusDays(1);
        return day;
    }

    /** Reads the stored comma-separated weekdays, falling back to Tuesday and Friday if none can be read. */
    public static Set<DayOfWeek> parseDays(String value) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (value != null) {
            for (String part : value.split(",")) {
                if (part.isBlank()) continue;
                try {
                    days.add(DayOfWeek.valueOf(part.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown names are skipped; the default below covers an empty result.
                }
            }
        }
        return days.isEmpty() ? EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY) : days;
    }

    public static String formatDays(Set<DayOfWeek> days) {
        return Arrays.stream(DayOfWeek.values()).filter(days::contains).map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }
}
