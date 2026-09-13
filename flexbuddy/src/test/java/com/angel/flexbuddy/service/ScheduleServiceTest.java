package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.CalendarDaySummary;
import com.angel.flexbuddy.dto.CalendarMonthResponse;
import com.angel.flexbuddy.dto.ShiftConflict;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.UpcomingDay;
import com.angel.flexbuddy.dto.UpcomingResponse;
import com.angel.flexbuddy.dto.UpcomingWeek;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");

    @Mock ShiftService shiftService;
    @Mock UserTimeService userTime;
    @InjectMocks ScheduleService scheduleService;

    @Test
    void upcoming_separatesFinishedBlocksGroupsByDayAndWeekAndFlagsOverlaps() {
        when(userTime.zone(EMAIL)).thenReturn(CHICAGO);
        when(userTime.now(CHICAGO)).thenReturn(LocalDateTime.of(2026, 9, 12, 14, 0, 30));
        List<Shift> scheduled = List.of(
                shift(1L, ShiftStatus.SCHEDULED, LocalDate.of(2026, 9, 12), "08:00", "12:00", "70.00"),
                shift(2L, ShiftStatus.SCHEDULED, LocalDate.of(2026, 9, 12), "15:15", "19:15", "84.00"),
                shift(3L, ShiftStatus.SCHEDULED, LocalDate.of(2026, 9, 13), "15:00", "18:00", "60.00"),
                shift(4L, ShiftStatus.SCHEDULED, LocalDate.of(2026, 9, 13), "17:00", "20:00", "66.00"),
                shift(5L, ShiftStatus.SCHEDULED, LocalDate.of(2026, 9, 15), "10:00", "12:00", "40.00"));
        when(shiftService.findScheduled(EMAIL, null, LocalDate.of(2026, 9, 25))).thenReturn(scheduled);
        when(shiftService.toResponses(EMAIL, scheduled)).thenReturn(scheduled.stream().map(this::response).toList());

        UpcomingResponse result = scheduleService.upcoming(EMAIL, 14);

        assertThat(result.now()).isEqualTo(LocalDateTime.of(2026, 9, 12, 14, 0, 30));
        assertThat(result.timeZone()).isEqualTo("America/Chicago");
        assertThat(result.next().getId()).isEqualTo(2L);
        assertThat(result.needsConfirmation()).extracting(ShiftResponse::getId).containsExactly(1L);
        assertThat(result.days()).extracting(UpcomingDay::date)
                .containsExactly(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 15));
        assertThat(result.days().get(1).plannedMinutes()).isEqualTo(360);
        assertThat(result.days().get(1).expectedPay()).isEqualByComparingTo("126.00");
        assertThat(result.weeks()).extracting(UpcomingWeek::weekStart)
                .containsExactly(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14));
        assertThat(result.weeks().getFirst().shifts()).isEqualTo(3);
        assertThat(result.weeks().getFirst().expectedPay()).isEqualByComparingTo("210.00");
        assertThat(result.conflicts()).containsExactly(new ShiftConflict(3L, 4L));
    }

    @Test
    void upcoming_rejectsARangeOutsideOneToSixtyTwoDays() {
        assertThatThrownBy(() -> scheduleService.upcoming(EMAIL, 0)).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> scheduleService.upcoming(EMAIL, 63)).isInstanceOf(InvalidFilterException.class);
        verifyNoInteractions(shiftService);
    }

    @Test
    void month_summarisesEachDayFromEveryStatus() {
        LocalDate sunday = LocalDate.of(2026, 9, 13);
        LocalDate monday = LocalDate.of(2026, 9, 14);
        when(shiftService.findFiltered(eq(EMAIL), argThat(filter -> filter.statuses().equals(ShiftStatus.ALL)
                && filter.from().equals(LocalDate.of(2026, 9, 1)) && filter.to().equals(LocalDate.of(2026, 9, 30)))))
                .thenReturn(List.of(
                        shift(1L, ShiftStatus.SCHEDULED, sunday, "15:15", "19:15", "84.00"),
                        shift(2L, ShiftStatus.COMPLETED, sunday, "08:00", "10:00", "40.00", "5.00"),
                        shift(3L, ShiftStatus.CANCELLED, monday, "08:00", "12:00", "18.00"),
                        shift(4L, ShiftStatus.FORFEITED, monday, "13:00", "17:00", "0.00")));

        CalendarMonthResponse result = scheduleService.month(EMAIL, YearMonth.of(2026, 9));

        assertThat(result.month()).isEqualTo("2026-09");
        assertThat(result.days()).containsExactly(
                new CalendarDaySummary(sunday, 1, 1, 0, 0, new BigDecimal("84.00"), new BigDecimal("45.00"), 360),
                new CalendarDaySummary(monday, 0, 0, 1, 1, new BigDecimal("0.00"), new BigDecimal("18.00"), 0));
    }

    private Shift shift(Long id, ShiftStatus status, LocalDate date, String start, String end, String basePay) {
        return shift(id, status, date, start, end, basePay, "0.00");
    }

    private Shift shift(Long id, ShiftStatus status, LocalDate date, String start, String end, String basePay,
            String tips) {
        Shift shift = new Shift(id, "VEA7", date, LocalTime.parse(start), LocalTime.parse(end),
                new BigDecimal(basePay), new BigDecimal(tips));
        shift.setStatus(status);
        return shift;
    }

    private ShiftResponse response(Shift shift) {
        ShiftResponse response = new ShiftResponse();
        response.setId(shift.getId());
        response.setStatus(shift.getStatus());
        return response;
    }
}
