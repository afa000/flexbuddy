package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.StandingEntryRequest;
import com.angel.flexbuddy.dto.StandingEventKind;
import com.angel.flexbuddy.dto.StandingResponse;
import com.angel.flexbuddy.exception.InvalidStandingException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.StandingEntry;
import com.angel.flexbuddy.model.StandingLevel;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.repository.StandingEntryRepository;

@ExtendWith(MockitoExtension.class)
class StandingServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Mock StandingEntryRepository entries;
    @Mock ShiftRepository shifts;
    @Mock AppUserRepository userRepository;
    @Mock UserTimeService userTime;

    private StandingService service;

    @BeforeEach
    void setUp() {
        service = new StandingService(entries, shifts, userRepository, userTime, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(userTime.today(EMAIL)).thenReturn(TODAY);
    }

    @Test
    void currentIsTheLatestEntryEvenWhenItIsOlderThanTheWindow() {
        StandingEntry old = entry(LocalDate.of(2026, 5, 1), StandingLevel.GREAT, null);
        when(entries.findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(eq(EMAIL), any()))
                .thenReturn(Optional.of(old));
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(eq(EMAIL), any(), any()))
                .thenReturn(List.of());
        when(entries.findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(EMAIL)).thenReturn(Optional.of(old));
        when(shifts.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(eq(EMAIL), any(), any(), any()))
                .thenReturn(List.of());

        StandingResponse window = service.window(EMAIL, 90);

        assertThat(window.current().level()).isEqualTo(StandingLevel.GREAT);
        assertThat(window.entries()).singleElement().satisfies(carried -> {
            assertThat(carried.recordedOn()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(carried.level()).isEqualTo(StandingLevel.GREAT);
        });
    }

    @Test
    void theWindowStartsWithTheEntryInForceAtItsFirstDay() {
        StandingEntry june = entry(LocalDate.of(2026, 6, 1), StandingLevel.GREAT, null);
        StandingEntry september = entry(LocalDate.of(2026, 9, 10), StandingLevel.FAIR, "After late forfeit");
        when(entries.findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(EMAIL, LocalDate.of(2026, 7, 2)))
                .thenReturn(Optional.of(june));
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(EMAIL, LocalDate.of(2026, 7, 2), TODAY))
                .thenReturn(List.of(september));
        when(entries.findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(EMAIL)).thenReturn(Optional.of(september));
        when(shifts.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(eq(EMAIL), any(), any(), any()))
                .thenReturn(List.of());

        StandingResponse window = service.window(EMAIL, 90);

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(window.to()).isEqualTo(TODAY);
        assertThat(window.entries()).extracting(entry -> entry.recordedOn() + " " + entry.level())
                .containsExactly("2026-06-01 GREAT", "2026-09-10 FAIR");
        assertThat(window.current().note()).isEqualTo("After late forfeit");
    }

    @Test
    void nothingLoggedMeansNoCurrentStanding() {
        when(entries.findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(eq(EMAIL), any()))
                .thenReturn(Optional.empty());
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(eq(EMAIL), any(), any()))
                .thenReturn(List.of());
        when(entries.findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(EMAIL)).thenReturn(Optional.empty());
        when(shifts.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(eq(EMAIL), any(), any(), any()))
                .thenReturn(List.of());

        StandingResponse window = service.window(EMAIL, 90);

        assertThat(window.current()).isNull();
        assertThat(window.entries()).isEmpty();
        assertThat(window.events()).isEmpty();
    }

    @Test
    void loggingTheSameDayAgainReplacesTheLevelAndNoteAndKeepsCreatedAt() {
        Instant created = Instant.parse("2026-09-10T12:00:00Z");
        StandingEntry existing = entry(LocalDate.of(2026, 9, 10), StandingLevel.FAIR, "first");
        existing.setCreatedAt(created);
        existing.setUpdatedAt(created);
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOn(EMAIL, LocalDate.of(2026, 9, 10))).thenReturn(Optional.of(existing));

        service.log(EMAIL, LocalDate.of(2026, 9, 10), new StandingEntryRequest(StandingLevel.AT_RISK, "second"));

        verify(entries).save(existing);
        assertThat(existing.getLevel()).isEqualTo(StandingLevel.AT_RISK);
        assertThat(existing.getNote()).isEqualTo("second");
        assertThat(existing.getCreatedAt()).isEqualTo(created);
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void aNewDayIsCreatedForTheOwner() {
        AppUser owner = new AppUser("Angel", EMAIL, "hash");
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOn(EMAIL, TODAY)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));

        service.log(EMAIL, TODAY, new StandingEntryRequest(StandingLevel.GREAT, null));

        ArgumentCaptor<StandingEntry> saved = ArgumentCaptor.forClass(StandingEntry.class);
        verify(entries).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(owner);
        assertThat(saved.getValue().getRecordedOn()).isEqualTo(TODAY);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void aDayAfterTodayInTheAccountTimeZoneIsRejectedAndTodayIsAccepted() {
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOn(EMAIL, TODAY)).thenReturn(Optional.of(entry(TODAY, StandingLevel.GREAT, null)));

        assertThatThrownBy(() -> service.log(EMAIL, LocalDate.of(2026, 9, 30), new StandingEntryRequest(StandingLevel.GREAT, null)))
                .isInstanceOf(InvalidStandingException.class)
                .hasMessage("Standing can be logged for today or an earlier day.");
        verify(entries, never()).save(any());

        service.log(EMAIL, TODAY, new StandingEntryRequest(StandingLevel.FAIR, null));

        verify(entries).save(any());
    }

    @Test
    void aBlankNoteIsStoredAsNullAndANoteIsTrimmed() {
        StandingEntry first = entry(TODAY, StandingLevel.GREAT, "old");
        StandingEntry second = entry(TODAY.minusDays(1), StandingLevel.GREAT, null);
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOn(EMAIL, TODAY)).thenReturn(Optional.of(first));
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOn(EMAIL, TODAY.minusDays(1))).thenReturn(Optional.of(second));

        service.log(EMAIL, TODAY, new StandingEntryRequest(StandingLevel.GREAT, "   "));
        service.log(EMAIL, TODAY.minusDays(1), new StandingEntryRequest(StandingLevel.GREAT, "  after the block  "));

        assertThat(first.getNote()).isNull();
        assertThat(second.getNote()).isEqualTo("after the block");
    }

    @Test
    void eventsListForfeitsLateForfeitsAndCancellationsOldestFirst() {
        Shift late = shift("LATE1", LocalDate.of(2026, 9, 8), ShiftStatus.FORFEITED);
        late.setLateForfeit(true);
        Shift forfeit = shift("VEA7", LocalDate.of(2026, 9, 12), ShiftStatus.FORFEITED);
        Shift cancelled = shift("BDL4", LocalDate.of(2026, 9, 20), ShiftStatus.CANCELLED);
        when(entries.findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(eq(EMAIL), any()))
                .thenReturn(Optional.empty());
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(eq(EMAIL), any(), any()))
                .thenReturn(List.of());
        when(entries.findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(EMAIL)).thenReturn(Optional.empty());
        when(shifts.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                EMAIL, Set.of(ShiftStatus.FORFEITED, ShiftStatus.CANCELLED), LocalDate.of(2026, 7, 2), TODAY))
                .thenReturn(List.of(late, forfeit, cancelled));

        StandingResponse window = service.window(EMAIL, 90);

        assertThat(window.events()).extracting(event -> event.date() + " " + event.station() + " " + event.kind())
                .containsExactly("2026-09-08 LATE1 " + StandingEventKind.LATE_FORFEIT,
                        "2026-09-12 VEA7 " + StandingEventKind.FORFEITED,
                        "2026-09-20 BDL4 " + StandingEventKind.CANCELLED);
        assertThat(window.events().getFirst().startTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void removingTheLatestEntryMakesThePreviousOneCurrent() {
        StandingEntry great = entry(LocalDate.of(2026, 8, 1), StandingLevel.GREAT, null);
        StandingEntry fair = entry(LocalDate.of(2026, 9, 10), StandingLevel.FAIR, null);
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOn(EMAIL, LocalDate.of(2026, 9, 10))).thenReturn(Optional.of(fair));

        service.remove(EMAIL, LocalDate.of(2026, 9, 10));

        verify(entries).delete(fair);
        when(entries.findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(eq(EMAIL), any()))
                .thenReturn(Optional.of(great));
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(eq(EMAIL), any(), any()))
                .thenReturn(List.of());
        when(entries.findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(EMAIL)).thenReturn(Optional.of(great));
        when(shifts.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(eq(EMAIL), any(), any(), any()))
                .thenReturn(List.of());
        assertThat(service.window(EMAIL, 90).current().level()).isEqualTo(StandingLevel.GREAT);
    }

    @Test
    void removingADayWithNoEntryIsNotAnError() {
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOn(EMAIL, TODAY)).thenReturn(Optional.empty());

        service.remove(EMAIL, TODAY);

        verify(entries, never()).delete(any());
    }

    @Test
    void aWindowOutsideSevenTo365DaysIsRejected() {
        when(entries.findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(eq(EMAIL), any()))
                .thenReturn(Optional.empty());
        when(entries.findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(eq(EMAIL), any(), any()))
                .thenReturn(List.of());
        when(entries.findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(EMAIL)).thenReturn(Optional.empty());
        when(shifts.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(eq(EMAIL), any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.window(EMAIL, 6)).isInstanceOf(InvalidStandingException.class)
                .hasMessage("Choose between 7 and 365 days.");
        assertThatThrownBy(() -> service.window(EMAIL, 366)).isInstanceOf(InvalidStandingException.class);
        assertThat(service.window(EMAIL, 7).from()).isEqualTo(TODAY.minusDays(6));
        assertThat(service.window(EMAIL, 365).from()).isEqualTo(TODAY.minusDays(364));
    }

    private static StandingEntry entry(LocalDate day, StandingLevel level, String note) {
        StandingEntry entry = new StandingEntry();
        entry.setRecordedOn(day);
        entry.setLevel(level);
        entry.setNote(note);
        entry.setCreatedAt(NOW);
        entry.setUpdatedAt(NOW);
        return entry;
    }

    private static Shift shift(String station, LocalDate date, ShiftStatus status) {
        Shift shift = new Shift(null, station, date, LocalTime.of(9, 0), LocalTime.of(13, 0),
                new BigDecimal("80.00"), BigDecimal.ZERO);
        shift.setStatus(status);
        return shift;
    }
}
