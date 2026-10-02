package com.angel.flexbuddy.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.angel.flexbuddy.mail.ErrorAlertMailer;

/**
 * Decides whether a failure becomes an email. The same error repeating sends one email per quiet period with a count
 * of the repeats, and all alerts together are capped per UTC day, so a job that fails every minute cannot fill an
 * inbox. State is in memory and resets on restart, which suits a single instance. Reporting never throws: a failing
 * alert must not become a second failure.
 */
@Service
public class ErrorAlertService {

    private static final Logger log = LoggerFactory.getLogger(ErrorAlertService.class);
    private static final int MAX_FINGERPRINTS = 500;
    private static final int MAX_ACCOUNTS = 1_000;
    private static final Duration BROWSER_WINDOW = Duration.ofHours(1);

    private static final class Seen {
        Instant lastSentAt;
        int suppressed;
    }

    private final ErrorAlertProperties properties;
    private final ErrorAlertMailer mailer;
    private final Clock clock;
    private final Map<String, Seen> fingerprints = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Seen> eldest) {
            return size() > MAX_FINGERPRINTS;
        }
    };
    private final Map<Long, Deque<Instant>> browserReports = new LinkedHashMap<>();
    private LocalDate day;
    private int sentToday;

    public ErrorAlertService(ErrorAlertProperties properties, ErrorAlertMailer mailer, Clock clock) {
        this.properties = properties;
        this.mailer = mailer;
        this.clock = clock;
    }

    public synchronized void report(ErrorReport report) {
        if (!properties.enabled()) {
            return;
        }
        try {
            Instant now = clock.instant();
            LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
            if (!today.equals(day)) {
                day = today;
                sentToday = 0;
            }
            Seen seen = fingerprints.computeIfAbsent(report.fingerprint(), key -> new Seen());
            boolean quiet = seen.lastSentAt != null && seen.lastSentAt.plus(properties.quietPeriod()).isAfter(now);
            if (quiet || sentToday >= properties.maxPerDay()) {
                seen.suppressed++;
                return;
            }
            int repeats = seen.suppressed;
            seen.suppressed = 0;
            seen.lastSentAt = now;
            sentToday++;
            mailer.send(ErrorAlertText.subject(report), ErrorAlertText.body(report, now, repeats));
        } catch (RuntimeException exception) {
            // WARN, never ERROR: an ERROR here would be reported and could loop. The class name only, no message.
            log.warn("error alert could not be handled ({})", exception.getClass().getSimpleName());
        }
    }

    /** Lets one account send a few browser reports an hour, so a page stuck in a loop cannot flood the alerts. */
    public synchronized boolean allowBrowserReport(long accountId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(BROWSER_WINDOW);
        browserReports.values().removeIf(times -> {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.removeFirst();
            }
            return times.isEmpty();
        });
        Deque<Instant> times = browserReports.get(accountId);
        if (times == null) {
            if (browserReports.size() >= MAX_ACCOUNTS) {
                Long oldest = browserReports.keySet().iterator().next();
                browserReports.remove(oldest);
            }
            times = new ArrayDeque<>();
            browserReports.put(accountId, times);
        }
        if (times.size() >= properties.browserReportsPerAccountPerHour()) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    synchronized int trackedFingerprints() {
        return fingerprints.size();
    }
}
