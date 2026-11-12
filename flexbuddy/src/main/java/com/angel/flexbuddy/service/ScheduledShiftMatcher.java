package com.angel.flexbuddy.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.DuplicateKind;
import com.angel.flexbuddy.dto.DuplicateMatch;
import com.angel.flexbuddy.i18n.Messages;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.ShiftRepository;

/** Finds the caller's logged blocks that an imported screenshot repeats, or scheduled ones it most likely completes. */
@Component
public class ScheduledShiftMatcher {

    static final long MINIMUM_OVERLAP_MINUTES = 30;

    private final ShiftRepository shiftRepository;

    public ScheduledShiftMatcher(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    /**
     * Finds the caller's completed and scheduled blocks that overlap the imported one, starting the day before so a
     * block that began late the previous evening is found. A scheduled block that a completed import most likely
     * finishes is {@code SCHEDULED_MATCH}; any other block is {@code EXACT} or {@code LIKELY}.
     */
    @Transactional(readOnly = true)
    public List<DuplicateMatch> match(String email, LocalDate date, LocalTime start, LocalTime end, String station,
            ShiftStatus importStatus) {
        if (email == null || date == null || start == null || end == null) return List.of();
        LocalDateTime candidateStart = LocalDateTime.of(date, start);
        long minutes = ChronoUnit.MINUTES.between(start, end);
        LocalDateTime candidateEnd = candidateStart.plusMinutes(minutes < 0 ? minutes + 24 * 60 : minutes);
        Locale locale = LocaleContextHolder.getLocale();
        return shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                        email, List.of(ShiftStatus.COMPLETED, ShiftStatus.SCHEDULED), date.minusDays(1), date).stream()
                .filter(shift -> stationMatches(station, shift.getStation()))
                .filter(shift -> overlapMinutes(candidateStart, candidateEnd,
                        shift.getStartDateTime(), shift.getEndDateTime()) >= MINIMUM_OVERLAP_MINUTES)
                .map(shift -> {
                    if (shift.getStatus() == ShiftStatus.SCHEDULED && importStatus == ShiftStatus.COMPLETED) {
                        return new DuplicateMatch(shift.getId(), DuplicateKind.SCHEDULED_MATCH,
                                Messages.current("import.scheduledMatch", shift.getStation(),
                                        Messages.dayFormat(locale).format(shift.getDate())));
                    }
                    boolean exact = shift.getDate().equals(date) && shift.getStartTime().equals(start)
                            && shift.getEndTime().equals(end);
                    return new DuplicateMatch(shift.getId(), exact ? DuplicateKind.EXACT : DuplicateKind.LIKELY,
                            Messages.current("import.alreadyLogged", shift.getStation(),
                                    Messages.dayFormat(locale).format(shift.getDate()),
                                    Messages.timeFormat(locale).format(shift.getStartTime())));
                })
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
