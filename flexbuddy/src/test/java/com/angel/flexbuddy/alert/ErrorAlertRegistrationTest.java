package com.angel.flexbuddy.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import com.angel.flexbuddy.mail.ErrorAlertMailer;
import com.angel.flexbuddy.security.MutableClock;

import ch.qos.logback.classic.Logger;

class ErrorAlertRegistrationTest {

    private static final class RecordingMailer implements ErrorAlertMailer {
        final List<String> bodies = new ArrayList<>();
        final List<String> subjects = new ArrayList<>();

        @Override
        public void send(String subject, String body) {
            subjects.add(subject);
            bodies.add(body);
        }
    }

    private final RecordingMailer mailer = new RecordingMailer();

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(AlertConfig.class)
                .withBean(ErrorAlertService.class)
                .withBean(ErrorAlertAppenderRegistrar.class)
                .withBean(ErrorAlertMailer.class, () -> mailer)
                .withBean(Clock.class, () -> new MutableClock(Instant.parse("2026-10-02T12:00:00Z")));
    }

    private void ready(ConfigurableApplicationContext context) {
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
    }

    private boolean rootHasTheAppender() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        return root.getAppender("errorAlerts") != null;
    }

    @Test
    void afterTheAppIsReadyAnErrorWithAnExceptionSendsOneEmailWithoutItsMessage() {
        runner().run(context -> {
            ready(context);
            LoggerFactory.getLogger("com.angel.flexbuddy.service.ShiftService")
                    .error("could not save", new IllegalStateException("driver@example.com 555-0100"));

            assertThat(mailer.subjects).hasSize(1);
            assertThat(mailer.subjects.get(0)).startsWith("[FlexBuddy] Server error: IllegalStateException");
            assertThat(mailer.bodies.get(0)).doesNotContain("driver@example.com").doesNotContain("555-0100");
        });
    }

    @Test
    void nothingIsReportedBeforeTheAppIsReady() {
        runner().run(context -> {
            LoggerFactory.getLogger("com.angel.flexbuddy.service.ShiftService").error("startup failure", new IllegalStateException("x"));

            assertThat(mailer.subjects).isEmpty();
        });
    }

    @Test
    void afterTheContextClosesTheRootLoggerNoLongerHasTheAppender() {
        runner().run(context -> {
            ready(context);
            assertThat(rootHasTheAppender()).isTrue();
        });

        assertThat(rootHasTheAppender()).isFalse();
    }

    @Test
    void disabledAttachesNothing() {
        runner().withPropertyValues("flexbuddy.alerts.enabled=false").run(context -> {
            ready(context);

            assertThat(rootHasTheAppender()).isFalse();
            LoggerFactory.getLogger("com.angel.flexbuddy.service.ShiftService").error("failed", new IllegalStateException("x"));
            assertThat(mailer.subjects).isEmpty();
        });
    }
}
