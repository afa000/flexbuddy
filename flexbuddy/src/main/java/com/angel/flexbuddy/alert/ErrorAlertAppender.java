package com.angel.flexbuddy.alert;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Feeds every ERROR the server logs to the alert service. One hook catches failed requests (Tomcat logs them), failed
 * scheduled jobs and failed async methods. Mail failures are ignored, because a failure to send must never send.
 */
public class ErrorAlertAppender extends AppenderBase<ILoggingEvent> {

    /** Loggers whose errors are never reported: the alert code, the mail code, and Hibernate's SQL echo. */
    private static final List<String> IGNORED = List.of(
            "com.angel.flexbuddy.alert",
            "com.angel.flexbuddy.mail",
            "org.springframework.mail",
            "jakarta.mail",
            "org.eclipse.angus.mail",
            // Echoes a SQL error that is also thrown, so it is reported once there; and expected duplicate-key
            // conflicts from retried offline changes would otherwise look like failures.
            "org.hibernate.engine.jdbc.spi.SqlExceptionHelper");

    private static final ThreadLocal<Boolean> REPORTING = new ThreadLocal<>();

    private final ErrorAlertService service;
    private final String buildId;

    public ErrorAlertAppender(ErrorAlertService service, String buildId) {
        this.service = service;
        this.buildId = buildId;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.ERROR) || ignored(event.getLoggerName())
                || Boolean.TRUE.equals(REPORTING.get())) {
            return;
        }
        REPORTING.set(Boolean.TRUE);
        try {
            service.report(ErrorReports.fromLogEvent(event, buildId));
        } catch (Throwable ignoredFailure) {
            // A full mail queue throws from the async proxy. Nothing here may reach the code that logged.
        } finally {
            REPORTING.remove();
        }
    }

    private static boolean ignored(String loggerName) {
        return loggerName != null && IGNORED.stream().anyMatch(loggerName::startsWith);
    }
}
