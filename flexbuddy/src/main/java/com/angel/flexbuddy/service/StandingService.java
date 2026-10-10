package com.angel.flexbuddy.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.StandingEntryRequest;
import com.angel.flexbuddy.dto.StandingEntryResponse;
import com.angel.flexbuddy.dto.StandingEventKind;
import com.angel.flexbuddy.dto.StandingEventResponse;
import com.angel.flexbuddy.dto.StandingResponse;
import com.angel.flexbuddy.exception.InvalidStandingException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.StandingEntry;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.repository.StandingEntryRepository;

/**
 * The Flex standing the driver logs by hand, next to the forfeits and cancellations that may have moved it. Standing
 * is only ever what was logged: nothing here works it out, and one entry is kept per day.
 */
@Service
public class StandingService {

    static final int MIN_DAYS = 7;
    static final int MAX_DAYS = 365;

    private final StandingEntryRepository entries;
    private final ShiftRepository shifts;
    private final AppUserRepository userRepository;
    private final UserTimeService userTime;
    private final Clock clock;

    public StandingService(StandingEntryRepository entries, ShiftRepository shifts, AppUserRepository userRepository,
            UserTimeService userTime, Clock clock) {
        this.entries = entries;
        this.shifts = shifts;
        this.userRepository = userRepository;
        this.userTime = userTime;
        this.clock = clock;
    }

    /** The last {@code days} days up to today in the driver's time zone. */
    @Transactional(readOnly = true)
    public StandingResponse window(String email, int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new InvalidStandingException("error.standing.dayRange", String.valueOf(MIN_DAYS), String.valueOf(MAX_DAYS));
        }
        LocalDate to = userTime.today(email);
        LocalDate from = to.minusDays(days - 1L);
        List<StandingEntryResponse> window = new ArrayList<>();
        // The entry in force on the first day keeps its real date, so the chart draws its step from the left edge.
        entries.findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(email, from)
                .map(StandingService::response).ifPresent(window::add);
        entries.findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(email, from, to)
                .forEach(entry -> window.add(response(entry)));
        StandingEntryResponse current = entries.findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(email)
                .map(StandingService::response).orElse(null);
        List<StandingEventResponse> events = shifts
                .findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                        email, Set.of(ShiftStatus.FORFEITED, ShiftStatus.CANCELLED), from, to)
                .stream().map(StandingService::event).toList();
        return new StandingResponse(from, to, current, window, events);
    }

    /** Logs the standing for a day, replacing any already logged for it. Today or an earlier day only. */
    @Transactional
    public void log(String email, LocalDate recordedOn, StandingEntryRequest request) {
        if (recordedOn.isAfter(userTime.today(email))) {
            throw new InvalidStandingException("error.standing.notFuture");
        }
        StandingEntry entry = entries.findByOwnerEmailIgnoreCaseAndRecordedOn(email, recordedOn).orElseGet(() -> {
            AppUser owner = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
            StandingEntry created = new StandingEntry();
            created.setOwner(owner);
            created.setRecordedOn(recordedOn);
            created.setCreatedAt(Instant.now(clock));
            return created;
        });
        entry.setLevel(request.level());
        entry.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        entry.setUpdatedAt(Instant.now(clock));
        entries.save(entry);
    }

    /** Forgets what was logged for a day. A day with nothing logged is not an error. */
    @Transactional
    public void remove(String email, LocalDate recordedOn) {
        entries.findByOwnerEmailIgnoreCaseAndRecordedOn(email, recordedOn).ifPresent(entries::delete);
    }

    private static StandingEntryResponse response(StandingEntry entry) {
        return new StandingEntryResponse(entry.getRecordedOn(), entry.getLevel(), entry.getNote());
    }

    private static StandingEventResponse event(Shift shift) {
        StandingEventKind kind = shift.getStatus() == ShiftStatus.CANCELLED ? StandingEventKind.CANCELLED
                : shift.isLateForfeit() ? StandingEventKind.LATE_FORFEIT : StandingEventKind.FORFEITED;
        return new StandingEventResponse(shift.getDate(), shift.getStartTime(), shift.getStation(), kind);
    }
}
