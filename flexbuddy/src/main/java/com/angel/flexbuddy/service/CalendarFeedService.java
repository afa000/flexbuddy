package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class CalendarFeedService {

    static final int DEFAULT_ALARM_MINUTES = 60;
    static final int FEED_HISTORY_DAYS = 30;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final AppUserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftService shiftService;
    private final UserTimeService userTime;
    private final IcsWriter icsWriter;

    public CalendarFeedService(AppUserRepository userRepository, ShiftRepository shiftRepository,
            ShiftService shiftService, UserTimeService userTime, IcsWriter icsWriter) {
        this.userRepository = userRepository;
        this.shiftRepository = shiftRepository;
        this.shiftService = shiftService;
        this.userTime = userTime;
        this.icsWriter = icsWriter;
    }

    @Transactional(readOnly = true)
    public String shift(String email, Long id, String appUrl) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        AppUser owner = shift.getOwner();
        return icsWriter.write(null, userTime.zone(owner), userTime.instant(),
                List.of(event(shift, owner, appUrl)), null);
    }

    /** The token feed is the only unauthenticated data read, so an unknown or malformed token finds nothing. */
    @Transactional(readOnly = true)
    public Optional<String> feed(String token, String appUrl) {
        if (token == null || !TOKEN.matcher(token).matches()) return Optional.empty();
        return userRepository.findByCalendarToken(token).map(owner -> {
            ZoneId zone = userTime.zone(owner);
            LocalDate today = userTime.now(zone).toLocalDate();
            ShiftFilter filter = ShiftFilter.report(today.minusDays(FEED_HISTORY_DAYS), null, null, null)
                    .withStatuses(ShiftStatus.ALL);
            List<IcsWriter.Event> events = shiftService.findFiltered(owner.getEmail(), filter).stream()
                    .sorted(Comparator.comparing(Shift::getDate).thenComparing(Shift::getStartTime))
                    .map(shift -> event(shift, owner, appUrl))
                    .toList();
            return icsWriter.write("FlexBuddy", zone, userTime.instant(), events, Duration.ofHours(1));
        });
    }

    private IcsWriter.Event event(Shift shift, AppUser owner, String appUrl) {
        ShiftStatus status = shift.getStatus();
        String station = shift.getStation();
        String summary = switch (status) {
            case SCHEDULED -> "Flex block · " + station + " · " + money(shift.getBasePay());
            case COMPLETED -> "Flex block · " + station + " · " + money(shift.getEarnedPay());
            case CANCELLED -> "Cancelled · Flex block · " + station;
            case FORFEITED -> "Forfeited · Flex block · " + station;
        };
        String description = switch (status) {
            case SCHEDULED -> "Scheduled block. Offered pay " + money(shift.getBasePay()) + ".";
            case COMPLETED -> "Completed. Earned " + money(shift.getEarnedPay()) + ".";
            case CANCELLED -> "Cancelled by Amazon.";
            case FORFEITED -> "Forfeited.";
        };
        Integer alarm = status != ShiftStatus.SCHEDULED ? null
                : owner.getRemindBeforeMinutes() == null ? DEFAULT_ALARM_MINUTES : owner.getRemindBeforeMinutes();
        return new IcsWriter.Event("shift-" + shift.getId() + "@flexbuddy", shift.getStartDateTime(),
                shift.getEndDateTime(), summary, description, appUrl,
                status == ShiftStatus.CANCELLED || status == ShiftStatus.FORFEITED, alarm);
    }

    private String money(BigDecimal value) {
        return "$" + (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
