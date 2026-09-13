package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class CalendarFeedServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final String TOKEN = "a".repeat(43);
    private static final String APP_URL = "https://flexbuddy.example/?screen=schedule";

    @Mock AppUserRepository userRepository;
    @Mock ShiftRepository shiftRepository;
    @Mock ShiftService shiftService;

    private CalendarFeedService service;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T15:00:00Z"), ZoneOffset.UTC);
        service = new CalendarFeedService(userRepository, shiftRepository, shiftService,
                new UserTimeService(userRepository, clock), new IcsWriter());
        owner = new AppUser("Angel", EMAIL, "hash");
        owner.setTimeZone("America/Chicago");
        owner.setRemindBeforeMinutes(120);
        owner.setCalendarToken(TOKEN);
    }

    @Test
    void feed_listsOnlyTheTokenOwnersShiftsWithAnAlarmOnScheduledBlocks() {
        when(userRepository.findByCalendarToken(TOKEN)).thenReturn(Optional.of(owner));
        when(shiftService.findFiltered(eq(EMAIL), any(ShiftFilter.class))).thenReturn(List.of(
                shift(7L, ShiftStatus.SCHEDULED, "84.00"), shift(8L, ShiftStatus.CANCELLED, "18.00")));

        String ics = service.feed(TOKEN, APP_URL).orElseThrow();

        assertThat(ics).contains("X-WR-CALNAME:FlexBuddy", "UID:shift-7@flexbuddy",
                "SUMMARY:Flex block · VEA7 · $84.00", "TRIGGER:-PT120M", "UID:shift-8@flexbuddy",
                "SUMMARY:Cancelled · Flex block · VEA7", "STATUS:CANCELLED",
                "DTSTART;TZID=America/Chicago:20260913T151500");
        assertThat(ics.split("BEGIN:VALARM", -1)).hasSize(2);
        // 15:00 UTC is 10:00 in Chicago, so the feed reaches back thirty days from Sep 12 local time.
        verify(shiftService).findFiltered(eq(EMAIL), argThat(filter -> filter.statuses().equals(ShiftStatus.ALL)
                && filter.from().equals(LocalDate.of(2026, 8, 13)) && filter.to() == null));
    }

    @Test
    void feed_findsNothingForAnUnknownOrMalformedToken() {
        String regenerated = "b".repeat(43);
        when(userRepository.findByCalendarToken(regenerated)).thenReturn(Optional.empty());

        assertThat(service.feed(regenerated, APP_URL)).isEmpty();
        assertThat(service.feed("../../etc/passwd", APP_URL)).isEmpty();
        assertThat(service.feed(null, APP_URL)).isEmpty();

        verify(userRepository, never()).findByCalendarToken("../../etc/passwd");
        verifyNoInteractions(shiftService);
    }

    @Test
    void shift_onlyExportsAShiftTheCallerOwns() {
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(9L, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.shift(EMAIL, 9L, APP_URL)).isInstanceOf(ShiftNotFoundException.class);
    }

    @Test
    void shift_writesOneEventWithTheOwnersLeadTime() {
        Shift shift = shift(7L, ShiftStatus.SCHEDULED, "84.00");
        shift.setOwner(owner);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, EMAIL)).thenReturn(Optional.of(shift));

        String ics = service.shift(EMAIL, 7L, APP_URL);

        assertThat(ics).contains("UID:shift-7@flexbuddy", "TRIGGER:-PT120M", "URL:" + APP_URL)
                .doesNotContain("X-WR-CALNAME");
    }

    private Shift shift(Long id, ShiftStatus status, String basePay) {
        Shift shift = new Shift(id, "VEA7", LocalDate.of(2026, 9, 13), LocalTime.of(15, 15), LocalTime.of(19, 15),
                new BigDecimal(basePay), BigDecimal.ZERO);
        shift.setStatus(status);
        return shift;
    }
}
