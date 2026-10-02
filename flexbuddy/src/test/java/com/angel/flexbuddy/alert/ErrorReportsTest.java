package com.angel.flexbuddy.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.angel.flexbuddy.dto.ClientErrorRequest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

class ErrorReportsTest {

    private final LoggerContext context = new LoggerContext();

    private LoggingEvent event(String loggerName, String message, Throwable throwable, Object... arguments) {
        Logger logger = context.getLogger(loggerName);
        return new LoggingEvent(Logger.class.getName(), logger, Level.ERROR, message, throwable, arguments);
    }

    private String everything(ErrorReport report) {
        return report.kind() + "|" + report.where() + "|" + String.join("|", report.details());
    }

    private Throwable deepApp(int depth) {
        if (depth == 0) {
            return new IllegalStateException("driver@example.com 555-0100");
        }
        return wrap(depth - 1);
    }

    private Throwable wrap(int depth) {
        return deepApp(depth);
    }

    @Test
    void anExceptionMessageNeverReachesTheReport() {
        Throwable cause = new IllegalStateException("driver@example.com 555-0100");
        Throwable wrapped = new DataIntegrityViolationException("could not execute statement [driver@example.com]", cause);

        ErrorReport report = ErrorReports.fromLogEvent(
                event("com.angel.flexbuddy.service.ShiftService", "could not save for {}", wrapped, "driver@example.com"), "b1");

        assertThat(everything(report)).doesNotContain("driver@example.com").doesNotContain("555-0100");
        assertThat(report.kind()).isEqualTo("DataIntegrityViolationException");
        assertThat(report.details()).contains("caused by IllegalStateException");
        assertThat(report.source()).isEqualTo(ErrorReport.Source.SERVER);
        assertThat(report.buildId()).isEqualTo("b1");
    }

    @Test
    void onlyThisAppsFramesAreKeptAndAtMostEight() {
        Throwable deep = deepApp(20);

        ErrorReport report = ErrorReports.fromLogEvent(event("com.angel.flexbuddy.service.X", "m", deep), "b1");

        List<String> frames = report.details().stream().filter(line -> line.contains("(")).toList();
        assertThat(frames).hasSize(8).allMatch(line -> line.startsWith("ErrorReportsTest.")
                && line.contains("(ErrorReportsTest.java:"));
        assertThat(report.where()).isEqualTo(frames.get(0));
        assertThat(everything(report)).doesNotContain("junit").doesNotContain("reflect");
    }

    @Test
    void withoutAnAppFrameTheFirstThreeFramesOfTheDeepestCauseAreUsed() {
        Throwable foreign = new IllegalStateException("x");
        foreign.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("org.example.Lib", "a", "Lib.java", 1),
                new StackTraceElement("org.example.Lib", "b", "Lib.java", 2),
                new StackTraceElement("org.example.Lib", "c", "Lib.java", 3),
                new StackTraceElement("org.example.Lib", "d", "Lib.java", 4)});

        ErrorReport report = ErrorReports.fromLogEvent(event("org.thirdparty.Thing", "m", foreign), "b1");

        assertThat(report.where()).isEqualTo("Thing");
        assertThat(report.details()).containsExactly("Lib.a(Lib.java:1)", "Lib.b(Lib.java:2)", "Lib.c(Lib.java:3)");
    }

    @Test
    void aLoggedErrorWithoutAThrowableKeepsTheTemplateOnlyForThisAppsLoggers() {
        ErrorReport app = ErrorReports.fromLogEvent(
                event("com.angel.flexbuddy.service.ReminderJob", "reminder failed for {}", null, "driver@example.com"), "b1");
        ErrorReport foreign = ErrorReports.fromLogEvent(event("org.hibernate.Thing", "secret {}", null, "x"), "b1");

        assertThat(app.kind()).isEqualTo("Logged error");
        assertThat(app.where()).isEqualTo("ReminderJob");
        assertThat(app.details()).containsExactly("reminder failed for {}");
        assertThat(foreign.details()).isEmpty();
    }

    private ClientErrorRequest browser(String message, String source, Integer line, Integer column, String screen, String buildId) {
        return new ClientErrorRequest(message, source, line, column, screen, buildId);
    }

    @Test
    void aBrowserSourceIsReducedToItsScriptPathOrPage() {
        assertThat(ErrorReports.fromBrowser(browser("TypeError: x is null", "https://flexbuddy.onrender.com/js/home.js?v=123",
                12, 5, "home", "123"), 42L).where()).isEqualTo("/js/home.js:12:5");
        assertThat(ErrorReports.fromBrowser(browser("TypeError: x", "https://evil.example/x.js", 1, 1, "home", "1"), 42L)
                .where()).isEqualTo("page:1:1");
        assertThat(ErrorReports.fromBrowser(browser("TypeError: x", null, null, null, "home", "1"), 42L).where())
                .isEqualTo("page:0:0");
    }

    @Test
    void aBrowserMessageIsScrubbedAndCut() {
        String token = "a".repeat(40);
        ErrorReport scrubbed = ErrorReports.fromBrowser(browser("TypeError: bad driver@example.com   and  " + token,
                "/js/app.js", 1, 1, "home", "1"), 42L);
        assertThat(scrubbed.details()).containsExactly("TypeError: bad [email] and [id]");

        ErrorReport cut = ErrorReports.fromBrowser(browser("x ".repeat(250), "/js/app.js", 1, 1, "home", "1"), 42L);
        assertThat(cut.details().get(0)).hasSizeLessThanOrEqualTo(300);
    }

    @Test
    void aBrowserErrorKeepsItsKindScreenAccountAndBuild() {
        ErrorReport report = ErrorReports.fromBrowser(
                browser("Uncaught TypeError: x is null", "/js/home.js", 3, 4, "dashboard", "20261002.1"), 42L);

        assertThat(report.kind()).isEqualTo("TypeError");
        assertThat(report.screen()).isEqualTo("home");
        assertThat(report.accountId()).isEqualTo(42L);
        assertThat(report.buildId()).isEqualTo("20261002.1");
        assertThat(report.source()).isEqualTo(ErrorReport.Source.BROWSER);

        assertThat(ErrorReports.fromBrowser(browser("oops", "/js/a.js", 1, 1, "weird", "bad build!"), 1L).screen())
                .isEqualTo("other");
        assertThat(ErrorReports.fromBrowser(browser("oops", "/js/a.js", 1, 1, "weird", "bad build!"), 1L).buildId())
                .isEqualTo("unknown");
        assertThat(ErrorReports.fromBrowser(browser("oops", "/js/a.js", 1, 1, "reports", "1"), 1L).kind()).isEqualTo("Error");
        assertThat(ErrorReports.fromBrowser(browser("oops", "/js/a.js", 1, 1, "reports", "1"), 1L).screen()).isEqualTo("reports");
    }
}
