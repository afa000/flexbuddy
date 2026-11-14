package com.angel.flexbuddy.api;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.service.ShiftService;
import com.angel.flexbuddy.service.UserTimeService;

/**
 * Read-only blocks and Home for the native app (FB-033). Figures come from the website's services; this class only
 * decides which blocks a sync returns and maps them to the API's shapes.
 */
@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiShiftService {
    /** A save that commits after a read can carry a slightly earlier time; changes this close are sent again. */
    static final Duration OVERLAP = Duration.ofMinutes(5);
    /** Older cursors reload everything: the 30-day purge may have removed deleted blocks without a trace. */
    static final Duration MAX_CURSOR_AGE = Duration.ofDays(29);
    /** Missing miles look back over the same days as the website's default. */
    static final int MISSING_MILES_DAYS = 7;

    private final ShiftRepository shifts;
    private final AppUserRepository users;
    private final ShiftService shiftService;
    private final ShiftReportService reports;
    private final UserTimeService userTime;
    private final Clock clock;

    public ApiShiftService(ShiftRepository shifts, AppUserRepository users, ShiftService shiftService,
            ShiftReportService reports, UserTimeService userTime, Clock clock) {
        this.shifts = shifts;
        this.users = users;
        this.shiftService = shiftService;
        this.reports = reports;
        this.userTime = userTime;
        this.clock = clock;
    }

    public record SyncAnswer(boolean full, String cursor, List<ApiShift> shifts, List<Long> deleted) {
    }

    /**
     * The blocks a phone needs: everything when {@code since} is null, too old, or from before blocks were removed
     * for good; otherwise the blocks changed since then and the ids of those deleted since then.
     */
    @Transactional(readOnly = true)
    public SyncAnswer sync(String email, Instant since) {
        // Read the cursor before the rows, so a change committed in between is sent again rather than skipped.
        Instant resetAt = users.findSyncResetAt(email);
        Instant latest = latest(shifts.latestSyncChange(email), resetAt);
        boolean full = since == null
                || since.isBefore(Instant.now(clock).minus(MAX_CURSOR_AGE))
                || (resetAt != null && since.isBefore(resetAt.plus(OVERLAP)));
        String cursor = SyncCursor.encode(latest);
        if (full) {
            return new SyncAnswer(true, cursor, map(email, shifts.findLiveForSync(email)), List.of());
        }
        List<Shift> live = new ArrayList<>();
        List<Long> deleted = new ArrayList<>();
        for (Shift shift : shifts.findChangedSince(email, since.minus(OVERLAP))) {
            if (shift.getDeletedAt() == null) live.add(shift);
            else deleted.add(shift.getId());
        }
        deleted.sort(null);
        return new SyncAnswer(false, cursor, map(email, live), deleted);
    }

    /** One block not in Recently deleted; the website's not-found exception otherwise. */
    @Transactional(readOnly = true)
    public ApiShift shift(String email, long id) {
        return ApiShift.of(shiftService.getShift(email, id));
    }

    @Transactional(readOnly = true)
    public ApiHome home(String email) {
        LocalDate today = userTime.today(email);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate sevenStart = today.minusDays(6);
        return new ApiHome(ApiValues.date(today),
                ApiHome.Statistics.of(weekStart, weekEnd,
                        reports.statistics(email, ShiftFilter.report(weekStart, weekEnd, null, null))),
                ApiHome.Statistics.of(sevenStart, today,
                        reports.statistics(email, ShiftFilter.report(sevenStart, today, null, null))),
                ApiHome.Goals.of(reports.goals(email)),
                shiftService.missingMiles(email, MISSING_MILES_DAYS).stream()
                        .map(com.angel.flexbuddy.dto.ShiftResponse::getId).toList());
    }

    private List<ApiShift> map(String email, List<Shift> rows) {
        if (rows.isEmpty()) return List.of();
        return shiftService.toResponses(email, rows).stream().map(ApiShift::of).toList();
    }

    private static Instant latest(Instant change, Instant reset) {
        Instant latest = Instant.EPOCH;
        if (change != null && change.isAfter(latest)) latest = change;
        if (reset != null && reset.isAfter(latest)) latest = reset;
        return latest;
    }
}
