package com.angel.flexbuddy.alert;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

class ErrorAlertAppenderTest {

    private LoggerContext context;
    private Logger root;
    private ErrorAlertService service;
    private ErrorAlertAppender appender;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        service = mock(ErrorAlertService.class);
        appender = new ErrorAlertAppender(service, "b1");
        appender.setContext(context);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        context.stop();
    }

    @Test
    void aWarningIsNotReportedButAnErrorIsReportedOnce() {
        Logger logger = context.getLogger("com.angel.flexbuddy.service.ShiftService");

        logger.warn("slow");
        verify(service, never()).report(any());

        logger.error("failed", new IllegalStateException("x"));
        verify(service, times(1)).report(any());
    }

    @Test
    void mailFailuresAreNeverReported() {
        for (String name : new String[] {"org.springframework.mail.MailSendException", "com.angel.flexbuddy.mail.SmtpErrorAlertMailer",
                "jakarta.mail.Transport", "org.eclipse.angus.mail.smtp.SMTPTransport", "com.angel.flexbuddy.alert.ErrorAlertService"}) {
            context.getLogger(name).error("boom");
        }

        verify(service, never()).report(any());
    }

    @Test
    void hibernatesEchoOfASqlErrorIsNotReportedTwice() {
        context.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper").error("ERROR: duplicate key value");

        verify(service, never()).report(any());
    }

    @Test
    void anErrorLoggedWhileReportingIsNotReportedAgain() {
        doAnswer(invocation -> {
            context.getLogger("org.example.Other").error("while reporting");
            return null;
        }).when(service).report(any());

        context.getLogger("com.angel.flexbuddy.service.ShiftService").error("first");

        verify(service, times(1)).report(any());
    }

    @Test
    void aFullMailQueueNeverReachesTheCodeThatLogged() {
        doThrow(new TaskRejectedException("queue is full")).when(service).report(any());

        // Must not throw.
        context.getLogger("com.angel.flexbuddy.service.ShiftService").error("failed", new IllegalStateException("x"));
    }
}
