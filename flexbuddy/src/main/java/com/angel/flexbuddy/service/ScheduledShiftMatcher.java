package com.angel.flexbuddy.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.DuplicateKind;
import com.angel.flexbuddy.dto.DuplicateMatch;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.ShiftRepository;

/** Finds the caller's scheduled blocks that an imported earnings screenshot most likely completes. */
@Component
public class ScheduledShiftMatcher {

    static final long MINIMUM_OVERLAP_MINUTES = 30;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);

    private final ShiftRepository shiftRepository;

    public ScheduledShiftMatcher(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    @Transactional(readOnly = true)
    public List<DuplicateMatch> match(String email, LocalDate date, LocalTime start, LocalTime end, String station) {
        if (email == null || date == null || start == null || end == null) return List.of();
        LocalDateTime candidateStart = LocalDateTime.of(date, start);
        long minutes = ChronoUnit.MINUTES.between(start, end);
        LocalDateTime candidateEnd = candidateStart.plusMinutes(minutes < 0 ? minutes + 24 * 60 : minutes);
        return shiftRepository.findByOwnerEmailIgnoreCaseAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(
                        email, ShiftStatus.SCHEDULED, date, date).stream()
                .filter(shift -> stationMatches(station, shift.getStation()))
                .filter(shift -> overlapMinutes(candidateStart, candidateEnd,
                        shift.getStartDateTime(), shift.getEndDateTime()) >= MINIMUM_OVERLAP_MINUTES)
                .map(shift -> new DuplicateMatch(shift.getId(), DuplicateKind.SCHEDULED_MATCH,
                        "This looks like your scheduled " + shift.getStation() + " block on "
                                + DAY_LABEL.format(shift.getDate()) + ". Complete it?"))
                .toList();
    }

    static long overlapMinutes(LocalDateTime firstStart, LocalDateTime firstEnd,
            LocalDateTime secondStart, LocalDateTime secondEnd) {
        LocalDateTime start = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        LocalDateTime end = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return end.isAfter(start) ? Duration.between(start, end).toMinutes() : 0;
    }

    private boolean stationMatches(String candidate, String scheduled) {
        if (candidate == null || candidate.isBlank()) return true;
        if (scheduled == null) return false;
        String imported = candidate.trim().toLowerCase(Locale.ROOT);
        String planned = scheduled.trim().toLowerCase(Locale.ROOT);
        return imported.equals(planned) || imported.contains(planned) || planned.contains(imported);
    }
}
