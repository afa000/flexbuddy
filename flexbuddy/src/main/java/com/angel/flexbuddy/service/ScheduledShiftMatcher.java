package com.angel.flexbuddy.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.DuplicateKind;
import com.angel.flexbuddy.dto.DuplicateMatch;
import com.angel.flexbuddy.i18n.Messages;
import com.angel.flexbuddy.importcore.BlockMatchRules;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.ShiftRepository;

/** Finds the caller's logged blocks that an imported screenshot repeats, or scheduled ones it most likely completes. */
@Component
public class ScheduledShiftMatcher {

    static final long MINIMUM_OVERLAP_MINUTES = BlockMatchRules.MINIMUM_OVERLAP_MINUTES;

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
        var candidate = new BlockMatchRules.Block(date, start, end, station, importStatus == ShiftStatus.SCHEDULED);
        Locale locale = LocaleContextHolder.getLocale();
        return shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                        email, List.of(ShiftStatus.COMPLETED, ShiftStatus.SCHEDULED), date.minusDays(1), date).stream()
                .map(shift -> {
                    var existing = new BlockMatchRules.Block(shift.getDate(), shift.getStartTime(), shift.getEndTime(),
                            shift.getStation(), shift.getStatus() == ShiftStatus.SCHEDULED);
                    var kind = BlockMatchRules.match(candidate, importStatus == ShiftStatus.COMPLETED, existing);
                    if (kind == null) return null;
                    if (kind == BlockMatchRules.Kind.SCHEDULED_MATCH) {
                        return new DuplicateMatch(shift.getId(), DuplicateKind.SCHEDULED_MATCH,
                                Messages.current("import.scheduledMatch", shift.getStation(),
                                        Messages.dayFormat(locale).format(shift.getDate())));
                    }
                    return new DuplicateMatch(shift.getId(), DuplicateKind.valueOf(kind.name()),
                            Messages.current("import.alreadyLogged", shift.getStation(),
                                    Messages.dayFormat(locale).format(shift.getDate()),
                                    Messages.timeFormat(locale).format(shift.getStartTime())));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    static long overlapMinutes(LocalDateTime firstStart, LocalDateTime firstEnd,
            LocalDateTime secondStart, LocalDateTime secondEnd) {
        return BlockMatchRules.overlapMinutes(firstStart, firstEnd, secondStart, secondEnd);
    }
}
