package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.DuplicateKind;
import com.angel.flexbuddy.dto.DuplicateMatch;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ScheduledShiftMatcherTest {

    private static final String EMAIL = "angel@example.com";
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    @Mock ShiftRepository shiftRepository;
    @InjectMocks ScheduledShiftMatcher matcher;

    @BeforeEach
    void setUp() {
        lenient().when(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(
                EMAIL, ShiftStatus.SCHEDULED, SUNDAY, SUNDAY))
                .thenReturn(List.of(scheduled(41L, "VEA7", SUNDAY, LocalTime.of(15, 15), LocalTime.of(19, 15))));
    }

    @Test
    void matchesWhenTheBlocksOverlapByExactlyThirtyMinutes() {
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(18, 45), LocalTime.of(21, 0), "VEA7"))
                .containsExactly(new DuplicateMatch(41L, DuplicateKind.SCHEDULED_MATCH,
                        "This looks like your scheduled VEA7 block on Sun Sep 13. Complete it?"));
    }

    @Test
    void ignoresAnOverlapShorterThanThirtyMinutes() {
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(18, 46), LocalTime.of(21, 0), "VEA7")).isEmpty();
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(12, 0), LocalTime.of(15, 44), "VEA7")).isEmpty();
    }

    @Test
    void requiresTheStationToMatchUnlessTheScreenshotHasNone() {
        LocalTime start = LocalTime.of(15, 15);
        LocalTime end = LocalTime.of(19, 15);

        assertThat(matcher.match(EMAIL, SUNDAY, start, end, "BDL4")).isEmpty();
        assertThat(matcher.match(EMAIL, SUNDAY, start, end, null)).hasSize(1);
        assertThat(matcher.match(EMAIL, SUNDAY, start, end, " ")).hasSize(1);
        assertThat(matcher.match(EMAIL, SUNDAY, start, end, "Windsor (vea7)")).hasSize(1);
    }

    @Test
    void comparesBlocksThatRunPastMidnight() {
        LocalDate monday = SUNDAY.plusDays(1);
        when(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(
                EMAIL, ShiftStatus.SCHEDULED, monday, monday))
                .thenReturn(List.of(scheduled(42L, "VEA7", monday, LocalTime.of(22, 0), LocalTime.of(2, 0))));

        assertThat(matcher.match(EMAIL, monday, LocalTime.of(23, 30), LocalTime.of(1, 30), "VEA7"))
                .extracting(DuplicateMatch::shiftId).containsExactly(42L);
    }

    @Test
    void needsAnAccountDateAndBothTimesBeforeLookingAnythingUp() {
        LocalTime start = LocalTime.of(15, 15);
        LocalTime end = LocalTime.of(19, 15);

        assertThat(matcher.match(null, SUNDAY, start, end, "VEA7")).isEmpty();
        assertThat(matcher.match(EMAIL, null, start, end, "VEA7")).isEmpty();
        assertThat(matcher.match(EMAIL, SUNDAY, null, end, "VEA7")).isEmpty();
        verifyNoInteractions(shiftRepository);
    }

    private Shift scheduled(Long id, String station, LocalDate date, LocalTime start, LocalTime end) {
        Shift shift = new Shift(id, station, date, start, end, new BigDecimal("84.00"), BigDecimal.ZERO);
        shift.setStatus(ShiftStatus.SCHEDULED);
        return shift;
    }
}
