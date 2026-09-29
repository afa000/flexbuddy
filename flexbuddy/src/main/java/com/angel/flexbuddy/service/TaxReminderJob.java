package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.PushMessage;
import com.angel.flexbuddy.dto.TaxQuarterResponse;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.TaxReminderKind;
import com.angel.flexbuddy.model.TaxReminderLog;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.TaxReminderLogRepository;

/**
 * Sends a push reminder a week before and on each estimated-tax due date to drivers who asked for them. Only an exact
 * date match sends, and only from 9:00 in the driver's own time zone, so a reminder never arrives at night or for a
 * date that has passed. The log guarantees each one goes out at most once, and the hourly run means a restart during
 * the day still sends later that day. Every figure is an estimate from the driver's own numbers.
 */
@Component
public class TaxReminderJob {

    static final int SEND_HOUR = 9;
    private static final String TAXES_URL = "/account#taxes";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH);

    private final AppUserRepository userRepository;
    private final TaxReminderLogRepository logRepository;
    private final TaxService taxService;
    private final PushService pushService;
    private final UserTimeService userTime;
    private final Clock clock;

    public TaxReminderJob(AppUserRepository userRepository, TaxReminderLogRepository logRepository,
            TaxService taxService, PushService pushService, UserTimeService userTime, Clock clock) {
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.taxService = taxService;
        this.pushService = pushService;
        this.userTime = userTime;
        this.clock = clock;
    }

    @Scheduled(cron = "0 12 * * * *")
    @Transactional
    public int sendTaxReminders() {
        if (!pushService.isConfigured()) return 0;
        Instant now = Instant.now(clock);
        int sent = 0;
        for (AppUser owner : userRepository.findByRemindTaxTrue()) {
            LocalDateTime local = LocalDateTime.ofInstant(now, userTime.zone(owner));
            if (local.getHour() < SEND_HOUR) continue;
            LocalDate today = local.toLocalDate();
            // January's due date belongs to the previous tax year's fourth quarter, so both years are looked at.
            for (int taxYear = today.getYear() - 1; taxYear <= today.getYear(); taxYear++) {
                for (int quarter = 1; quarter <= 4; quarter++) {
                    LocalDate due = taxService.dueDate(taxYear, quarter);
                    TaxReminderKind kind = today.equals(due) ? TaxReminderKind.DUE_DAY
                            : today.equals(due.minusDays(7)) ? TaxReminderKind.WEEK_BEFORE : null;
                    if (kind == null || !claim(owner, due, kind, now)) continue;
                    pushService.send(owner, message(owner, taxYear, quarter, due, kind));
                    sent++;
                }
            }
        }
        return sent;
    }

    private PushMessage message(AppUser owner, int taxYear, int quarter, LocalDate due, TaxReminderKind kind) {
        String title = kind == TaxReminderKind.WEEK_BEFORE ? "Estimated tax due " + DAY.format(due)
                : "Estimated tax due today · Q" + quarter;
        TaxQuarterResponse period = taxService.summary(owner.getEmail(), taxYear).quarters().get(quarter - 1);
        String body = period.setAside() == null
                ? "Q" + quarter + " payment is due. Choose a set-aside percentage in FlexBuddy to see an estimate."
                : "Q" + quarter + " estimate to set aside: " + money(period.setAside()) + " · " + money(period.paid())
                        + " recorded as paid. An estimate from your own numbers, not tax advice.";
        String tag = "tax-" + taxYear + "-q" + quarter + (kind == TaxReminderKind.WEEK_BEFORE ? "-week" : "-due");
        return new PushMessage(title, body, TAXES_URL, tag);
    }

    /** Records the reminder before it is sent, so at most one of each is ever sent. False when it already was. */
    private boolean claim(AppUser owner, LocalDate due, TaxReminderKind kind, Instant now) {
        if (logRepository.existsById(new TaxReminderLog.Key(owner.getId(), due, kind))) return false;
        logRepository.save(new TaxReminderLog(owner.getId(), due, kind, now));
        return true;
    }

    private static String money(BigDecimal value) {
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
