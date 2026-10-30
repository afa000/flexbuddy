package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private static final List<ShiftStatus> LOGGED = List.of(ShiftStatus.COMPLETED, ShiftStatus.SCHEDULED);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    @Mock ShiftRepository shiftRepository;
    @InjectMocks ScheduledShiftMatcher matcher;

    @BeforeEach
    void setUp() {
        lenient().when(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                EMAIL, LOGGED, SUNDAY.minusDays(1), SUNDAY))
                .thenReturn(List.of(block(41L, ShiftStatus.SCHEDULED, "VEA7", SUNDAY, LocalTime.of(15, 15), LocalTime.of(19, 15))));
    }

    @Test
    void matchesWhenTheBlocksOverlapByExactlyThirtyMinutes() {
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(18, 45), LocalTime.of(21, 0), "VEA7", ShiftStatus.COMPLETED))
                .containsExactly(new DuplicateMatch(41L, DuplicateKind.SCHEDULED_MATCH,
                        "This looks like your scheduled VEA7 block on Sun Sep 13. Complete it?"));
    }

    @Test
    void ignoresAnOverlapShorterThanThirtyMinutes() {
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(18, 46), LocalTime.of(21, 0), "VEA7", ShiftStatus.COMPLETED)).isEmpty();
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(12, 0), LocalTime.of(15, 44), "VEA7", ShiftStatus.COMPLETED)).isEmpty();
    }

    @Test
    void requiresTheStationToMatchUnlessTheScreenshotHasNone() {
        LocalTime start = LocalTime.of(15, 15);
        LocalTime end = LocalTime.of(19, 15);

        assertThat(matcher.match(EMAIL, SUNDAY, start, end, "BDL4", ShiftStatus.COMPLETED)).isEmpty();
        assertThat(matcher.match(EMAIL, SUNDAY, start, end, null, ShiftStatus.COMPLETED)).hasSize(1);
        assertThat(matcher.match(EMAIL, SUNDAY, start, end, " ", ShiftStatus.COMPLETED)).hasSize(1);
        assertThat(matcher.match(EMAIL, SUNDAY, start, end, "Windsor (vea7)", ShiftStatus.COMPLETED)).hasSize(1);
    }

    @Test
    void comparesBlocksThatRunPastMidnight() {
        LocalDate monday = SUNDAY.plusDays(1);
        when(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                EMAIL, LOGGED, SUNDAY, monday))
                .thenReturn(List.of(block(42L, ShiftStatus.SCHEDULED, "VEA7", SUNDAY, LocalTime.of(22, 0), LocalTime.of(2, 0))));

        assertThat(matcher.match(EMAIL, monday, LocalTime.of(0, 30), LocalTime.of(1, 30), "VEA7", ShiftStatus.COMPLETED))
                .extracting(DuplicateMatch::shiftId).containsExactly(42L);
    }

    @Test
    void reportsACompletedBlockWithTheSameTimesAsExactForEitherKindOfImport() {
        when(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(EMAIL, LOGGED, SUNDAY.minusDays(1), SUNDAY))
                .thenReturn(List.of(block(50L, ShiftStatus.COMPLETED, "VEA7", SUNDAY, LocalTime.of(15, 15), LocalTime.of(19, 15))));

        for (ShiftStatus importStatus : List.of(ShiftStatus.COMPLETED, ShiftStatus.SCHEDULED)) {
            assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(15, 15), LocalTime.of(19, 15), "VEA7", importStatus))
                    .containsExactly(new DuplicateMatch(50L, DuplicateKind.EXACT,
                            "You already have a VEA7 block on Sun Sep 13 at 3:15 PM."));
        }
    }

    @Test
    void reportsACompletedBlockShiftedByThirtyMinutesAsLikely() {
        when(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(EMAIL, LOGGED, SUNDAY.minusDays(1), SUNDAY))
                .thenReturn(List.of(block(50L, ShiftStatus.COMPLETED, "VEA7", SUNDAY, LocalTime.of(15, 15), LocalTime.of(19, 15))));

        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(15, 45), LocalTime.of(19, 45), "VEA7", ShiftStatus.COMPLETED))
                .extracting(DuplicateMatch::shiftId, DuplicateMatch::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(50L, DuplicateKind.LIKELY));
    }

    @Test
    void reportsAScheduledBlockAsExactOrLikelyWhenTheImportIsScheduledToo() {
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(15, 15), LocalTime.of(19, 15), "VEA7", ShiftStatus.SCHEDULED))
                .extracting(DuplicateMatch::kind).containsExactly(DuplicateKind.EXACT);
        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(16, 0), LocalTime.of(19, 15), "VEA7", ShiftStatus.SCHEDULED))
                .extracting(DuplicateMatch::kind).containsExactly(DuplicateKind.LIKELY);
    }

    @Test
    void asksOnlyForCompletedAndScheduledBlocksOfTheCaller() {
        matcher.match(EMAIL, SUNDAY, LocalTime.of(15, 15), LocalTime.of(19, 15), "VEA7", ShiftStatus.COMPLETED);

        // Cancelled and forfeited blocks are never requested, and the trash is excluded by the entity's restriction.
        verify(shiftRepository).findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                EMAIL, List.of(ShiftStatus.COMPLETED, ShiftStatus.SCHEDULED), SUNDAY.minusDays(1), SUNDAY);
        verifyNoMoreInteractions(shiftRepository);
    }

    @Test
    void findsABlockFromTheEveningBeforeThatRunsPastMidnight() {
        LocalDate saturday = SUNDAY.minusDays(1);
        when(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(EMAIL, LOGGED, saturday, SUNDAY))
                .thenReturn(List.of(block(53L, ShiftStatus.COMPLETED, "VEA7", saturday, LocalTime.of(22, 0), LocalTime.of(2, 0))));

        assertThat(matcher.match(EMAIL, SUNDAY, LocalTime.of(0, 30), LocalTime.of(2, 0), "VEA7", ShiftStatus.COMPLETED))
                .extracting(DuplicateMatch::shiftId, DuplicateMatch::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(53L, DuplicateKind.LIKELY));
    }

    @Test
    void needsAnAccountDateAndBothTimesBeforeLookingAnythingUp() {
        LocalTime start = LocalTime.of(15, 15);
        LocalTime end = LocalTime.of(19, 15);

        assertThat(matcher.match(null, SUNDAY, start, end, "VEA7", ShiftStatus.COMPLETED)).isEmpty();
        assertThat(matcher.match(EMAIL, null, start, end, "VEA7", ShiftStatus.COMPLETED)).isEmpty();
        assertThat(matcher.match(EMAIL, SUNDAY, null, end, "VEA7", ShiftStatus.COMPLETED)).isEmpty();
        verifyNoInteractions(shiftRepository);
    }

    private Shift block(Long id, ShiftStatus status, String station, LocalDate date, LocalTime start, LocalTime end) {
        Shift shift = new Shift(id, station, date, start, end, new BigDecimal("84.00"), BigDecimal.ZERO);
        shift.setStatus(status);
        return shift;
    }
}
