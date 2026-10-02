package com.angel.flexbuddy.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.mail.ErrorAlertMailer;
import com.angel.flexbuddy.security.MutableClock;

class ErrorAlertServiceTest {

    private static final class RecordingMailer implements ErrorAlertMailer {
        final List<String> subjects = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        boolean failing;

        @Override
        public void send(String subject, String body) {
            if (failing) {
                throw new IllegalStateException("mail is down");
            }
            subjects.add(subject);
            bodies.add(body);
        }
    }

    private MutableClock clock;
    private RecordingMailer mailer;
    private ErrorAlertService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-02T12:00:00Z"));
        mailer = new RecordingMailer();
        service = serviceWith(new ErrorAlertProperties(true, "ops@example.com", Duration.ofHours(6), 20, 10));
    }

    private ErrorAlertService serviceWith(ErrorAlertProperties properties) {
        return new ErrorAlertService(properties, mailer, clock);
    }

    private ErrorReport report(String kind, String where) {
        return new ErrorReport(ErrorReport.Source.SERVER, kind, where, List.of(), null, null, "build-1");
    }

    @Test
    void theFirstReportOfAnErrorSendsOneEmail() {
        service.report(report("IllegalStateException", "ShiftService.update(ShiftService.java:1)"));

        assertThat(mailer.subjects).containsExactly(
                "[FlexBuddy] Server error: IllegalStateException at ShiftService.update");
    }

    @Test
    void theSameErrorAgainWithinTheQuietPeriodSendsNothing() {
        ErrorReport same = report("IllegalStateException", "ShiftService.update(ShiftService.java:1)");
        service.report(same);
        clock.advance(Duration.ofHours(5).plusMinutes(59));

        service.report(same);

        assertThat(mailer.subjects).hasSize(1);
    }

    @Test
    void afterTheQuietPeriodItSendsAgainAndCountsTheRepeats() {
        ErrorReport same = report("IllegalStateException", "ShiftService.update(ShiftService.java:1)");
        service.report(same);
        clock.advance(Duration.ofMinutes(10));
        service.report(same);
        service.report(same);
        clock.advance(Duration.ofHours(6));

        service.report(same);

        assertThat(mailer.subjects).hasSize(2);
        assertThat(mailer.bodies.get(1)).contains("Repeated 2 more times since the last email");
        assertThat(mailer.bodies.get(0)).doesNotContain("Repeated");
    }

    @Test
    void aDifferentPlaceWithTheSameKindSendsItsOwnEmail() {
        service.report(report("IllegalStateException", "ShiftService.update(ShiftService.java:1)"));
        service.report(report("IllegalStateException", "ExpenseService.save(ExpenseService.java:9)"));

        assertThat(mailer.subjects).hasSize(2);
    }

    @Test
    void theTwentyFirstEmailOfADayIsNotSentAndANewDayStartsAgain() {
        for (int i = 0; i < 21; i++) {
            service.report(report("Error" + i, "Where" + i));
        }
        assertThat(mailer.subjects).hasSize(20);

        clock.advance(Duration.ofHours(12));
        service.report(report("BrandNew", "Elsewhere"));

        assertThat(mailer.subjects).hasSize(21);
    }

    @Test
    void theDailyCountResetsAtUtcMidnight() {
        clock = new MutableClock(Instant.parse("2026-10-02T23:59:00Z"));
        service = serviceWith(new ErrorAlertProperties(true, "ops@example.com", Duration.ofHours(6), 1, 10));
        service.report(report("First", "A"));
        service.report(report("Second", "B"));
        assertThat(mailer.subjects).hasSize(1);

        clock.advance(Duration.ofMinutes(2));
        service.report(report("Second", "B"));

        assertThat(mailer.subjects).hasSize(2);
    }

    @Test
    void disabledSendsNothing() {
        service = serviceWith(new ErrorAlertProperties(false, "ops@example.com", Duration.ofHours(6), 20, 10));

        service.report(report("IllegalStateException", "ShiftService.update(ShiftService.java:1)"));

        assertThat(mailer.subjects).isEmpty();
    }

    @Test
    void anAccountMaySendTenBrowserReportsAnHourAndThenWaits() {
        for (int i = 0; i < 10; i++) {
            assertThat(service.allowBrowserReport(42L)).isTrue();
        }
        assertThat(service.allowBrowserReport(42L)).isFalse();
        // Another account is not affected.
        assertThat(service.allowBrowserReport(43L)).isTrue();

        clock.advance(Duration.ofMinutes(61));
        assertThat(service.allowBrowserReport(42L)).isTrue();
    }

    @Test
    void theNumberOfRememberedErrorsIsCapped() {
        for (int i = 0; i < 501; i++) {
            service.report(report("Error", "Place" + i));
        }

        assertThat(service.trackedFingerprints()).isEqualTo(500);
    }

    @Test
    void aMailerThatThrowsNeverMakesReportThrow() {
        mailer.failing = true;

        service.report(report("IllegalStateException", "ShiftService.update(ShiftService.java:1)"));

        assertThat(mailer.subjects).isEmpty();
    }
}
