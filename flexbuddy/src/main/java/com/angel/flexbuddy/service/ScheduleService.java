package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.CalendarDaySummary;
import com.angel.flexbuddy.dto.CalendarMonthResponse;
import com.angel.flexbuddy.dto.ShiftConflict;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.UpcomingDay;
import com.angel.flexbuddy.dto.UpcomingResponse;
import com.angel.flexbuddy.dto.UpcomingWeek;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;

@Service
public class ScheduleService {

    static final int MAX_UPCOMING_DAYS = 62;

    private final ShiftService shiftService;
    private final UserTimeService userTime;

    public ScheduleService(ShiftService shiftService, UserTimeService userTime) {
        this.shiftService = shiftService;
        this.userTime = userTime;
    }

    @Transactional(readOnly = true)
    public CalendarMonthResponse month(String email, YearMonth month) {
        ShiftFilter filter = ShiftFilter.report(month.atDay(1), month.atEndOfMonth(), null, null)
                .withStatuses(ShiftStatus.ALL);
        Map<LocalDate, DayTotals> days = new TreeMap<>();
        for (Shift shift : shiftService.findFiltered(email, filter)) {
            days.computeIfAbsent(shift.getDate(), DayTotals::new).add(shift);
        }
        return new CalendarMonthResponse(month.toString(), days.values().stream().map(DayTotals::summary).toList());
    }

    @Transactional(readOnly = true)
    public UpcomingResponse upcoming(String email, int days) {
        if (days < 1 || days > MAX_UPCOMING_DAYS) {
            throw new InvalidFilterException("days must be between 1 and " + MAX_UPCOMING_DAYS + ".");
        }
        ZoneId zone = userTime.zone(email);
        LocalDateTime now = userTime.now(zone);
        List<Shift> scheduled = shiftService.findScheduled(email, null, now.toLocalDate().plusDays(days - 1L));
        List<ShiftResponse> mapped = shiftService.toResponses(email, scheduled);
        Map<Long, ShiftResponse> responses = new HashMap<>();
        for (int index = 0; index < scheduled.size(); index++) {
            responses.put(scheduled.get(index).getId(), mapped.get(index));
        }

        List<Shift> upcoming = new ArrayList<>();
        List<ShiftResponse> needsConfirmation = new ArrayList<>();
        for (Shift shift : scheduled) {
            if (shift.getEndDateTime().isAfter(now)) upcoming.add(shift);
            else needsConfirmation.add(responses.get(shift.getId()));
        }

        Map<LocalDate, List<Shift>> byDay = new TreeMap<>();
        Map<LocalDate, List<Shift>> byWeek = new TreeMap<>();
        for (Shift shift : upcoming) {
            byDay.computeIfAbsent(shift.getDate(), ignored -> new ArrayList<>()).add(shift);
            byWeek.computeIfAbsent(shift.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    ignored -> new ArrayList<>()).add(shift);
        }
        List<UpcomingDay> dayList = byDay.entrySet().stream()
                .map(entry -> new UpcomingDay(entry.getKey(),
                        entry.getValue().stream().map(shift -> responses.get(shift.getId())).toList(),
                        plannedMinutes(entry.getValue()), expectedPay(entry.getValue())))
                .toList();
        List<UpcomingWeek> weekList = byWeek.entrySet().stream()
                .map(entry -> new UpcomingWeek(entry.getKey(), entry.getKey().plusDays(6), entry.getValue().size(),
                        plannedMinutes(entry.getValue()), expectedPay(entry.getValue())))
                .toList();

        return new UpcomingResponse(now.truncatedTo(ChronoUnit.SECONDS), zone.getId(),
                upcoming.isEmpty() ? null : responses.get(upcoming.getFirst().getId()),
                dayList, weekList, conflicts(upcoming), needsConfirmation);
    }

    static List<ShiftConflict> conflicts(List<Shift> shifts) {
        List<ShiftConflict> conflicts = new ArrayList<>();
        for (int first = 0; first < shifts.size(); first++) {
            for (int second = first + 1; second < shifts.size(); second++) {
                Shift a = shifts.get(first);
                Shift b = shifts.get(second);
                if (ScheduledShiftMatcher.overlapMinutes(a.getStartDateTime(), a.getEndDateTime(),
                        b.getStartDateTime(), b.getEndDateTime()) > 0) {
                    conflicts.add(new ShiftConflict(a.getId(), b.getId()));
                }
            }
        }
        return conflicts;
    }

    private static int plannedMinutes(List<Shift> shifts) {
        return shifts.stream().mapToInt(Shift::getTimeWorked).sum();
    }

    private static BigDecimal expectedPay(List<Shift> shifts) {
        return money(shifts.stream().map(Shift::getBasePay).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class DayTotals {
        private final LocalDate date;
        private int scheduled;
        private int completed;
        private int cancelled;
        private int forfeited;
        private int minutes;
        private BigDecimal expectedPay = BigDecimal.ZERO;
        private BigDecimal earned = BigDecimal.ZERO;

        private DayTotals(LocalDate date) {
            this.date = date;
        }

        private void add(Shift shift) {
            switch (shift.getStatus()) {
                case SCHEDULED -> {
                    scheduled++;
                    expectedPay = expectedPay.add(shift.getBasePay());
                    minutes += shift.getTimeWorked();
                }
                case COMPLETED -> completed++;
                case CANCELLED -> cancelled++;
                case FORFEITED -> forfeited++;
            }
            earned = earned.add(shift.getEarnedPay());
            minutes += shift.getWorkedMinutes();
        }

        private CalendarDaySummary summary() {
            return new CalendarDaySummary(date, scheduled, completed, cancelled, forfeited,
                    money(expectedPay), money(earned), minutes);
        }
    }
}
