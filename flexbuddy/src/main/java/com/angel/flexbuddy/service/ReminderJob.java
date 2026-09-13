package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.PushMessage;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.ReminderKind;
import com.angel.flexbuddy.model.ReminderLog;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.ReminderLogRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

/**
 * Sends push reminders. Due reminders are matched against a trailing window rather than an instant so an
 * instance that wakes up late still sends them, and reminder_log guarantees each one is sent at most once.
 */
@Component
public class ReminderJob {

    static final Duration REMINDER_WINDOW = Duration.ofMinutes(15);
    static final Duration CONFIRM_AFTER = Duration.ofHours(1);
    static final Duration CONFIRM_UNTIL = Duration.ofHours(24);
    private static final String SCHEDULE_URL = "/?screen=schedule";
    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ShiftRepository shiftRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final PushService pushService;
    private final UserTimeService userTime;
    private final Clock clock;

    public ReminderJob(ShiftRepository shiftRepository, ReminderLogRepository reminderLogRepository,
            PushService pushService, UserTimeService userTime, Clock clock) {
        this.shiftRepository = shiftRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.pushService = pushService;
        this.userTime = userTime;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${flexbuddy.push.reminder-delay-ms:60000}",
            initialDelayString = "${flexbuddy.push.reminder-delay-ms:60000}")
    @Transactional
    public int sendShiftReminders() {
        if (!pushService.isConfigured()) return 0;
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        int sent = 0;
        for (Shift shift : shiftRepository.findScheduledWithLeadTime(today.minusDays(1), today.plusDays(2))) {
            AppUser owner = shift.getOwner();
            Instant start = shift.getStartDateTime().atZone(userTime.zone(owner)).toInstant();
            Instant remindAt = start.minus(Duration.ofMinutes(owner.getRemindBeforeMinutes()));
            if (!isDue(remindAt, now) || !claim(shift, ReminderKind.BEFORE_START, now)) continue;
            pushService.send(owner, new PushMessage("Upcoming block · " + shift.getStation(),
                    "Starts at " + START_TIME.format(shift.getStartTime()) + " · "
                            + money(shift.getBasePay()) + " offered",
                    SCHEDULE_URL, "shift-" + shift.getId() + "-start"));
            sent++;
        }
        return sent;
    }

    @Scheduled(cron = "0 7 * * * *")
    @Transactional
    public int sendConfirmationNudges() {
        if (!pushService.isConfigured()) return 0;
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        int sent = 0;
        for (Shift shift : shiftRepository.findScheduledWithConfirmNudges(today.minusDays(2), today.plusDays(1))) {
            AppUser owner = shift.getOwner();
            Instant end = shift.getEndDateTime().atZone(userTime.zone(owner)).toInstant();
            boolean endedInWindow = !end.isAfter(now.minus(CONFIRM_AFTER)) && end.isAfter(now.minus(CONFIRM_UNTIL));
            if (!endedInWindow || !claim(shift, ReminderKind.CONFIRM, now)) continue;
            pushService.send(owner, new PushMessage("Did you work this block?",
                    shift.getStation() + " · " + DAY.format(shift.getDate()) + " "
                            + CLOCK_TIME.format(shift.getStartTime()) + "–" + CLOCK_TIME.format(shift.getEndTime())
                            + ". Confirm it in FlexBuddy.",
                    SCHEDULE_URL, "shift-" + shift.getId() + "-confirm"));
            sent++;
        }
        return sent;
    }

    static boolean isDue(Instant remindAt, Instant now) {
        return !remindAt.isAfter(now) && remindAt.isAfter(now.minus(REMINDER_WINDOW));
    }

    private boolean claim(Shift shift, ReminderKind kind, Instant now) {
        if (reminderLogRepository.existsById(new ReminderLog.Key(shift.getId(), kind))) return false;
        reminderLogRepository.save(new ReminderLog(shift.getId(), kind, now));
        return true;
    }

    private String money(BigDecimal value) {
        return "$" + (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
