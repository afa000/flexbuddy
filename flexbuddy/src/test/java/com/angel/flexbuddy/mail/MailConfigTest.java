package com.angel.flexbuddy.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class MailConfigTest {

    private static final String LINK = "https://flexbuddy.onrender.com/reset-password?token=SECRET-TOKEN-VALUE";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailConfig.class)
            .withPropertyValues("flexbuddy.mail.from=FlexBuddy <flexbuddysupport@gmail.com>");

    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger("com.angel.flexbuddy.mail");
        logger.setLevel(Level.INFO);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(logs);
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void withNoHostTheLoggingMailerIsUsed() {
        runner.run(context -> assertThat(context.getBean(PasswordResetMailer.class))
                .isInstanceOf(LoggingPasswordResetMailer.class));
    }

    @Test
    void anEmptyHostIsTheSameAsNoHost() {
        runner.withPropertyValues("spring.mail.host=")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context.getBean(PasswordResetMailer.class))
                        .isInstanceOf(LoggingPasswordResetMailer.class));
    }

    @Test
    void withAHostTheSmtpMailerIsUsed() {
        runner.withPropertyValues("spring.mail.host=smtp.example.com")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context.getBean(PasswordResetMailer.class))
                        .isInstanceOf(SmtpPasswordResetMailer.class));
    }

    @Test
    void aProductionLogNeverHoldsTheLink() {
        runner.withPropertyValues("flexbuddy.mail.log-links=false").run(context ->
                context.getBean(PasswordResetMailer.class).sendResetLink("angel@example.com", "Angel", LINK));

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).contains("mail is not configured")
                .doesNotContain("SECRET-TOKEN-VALUE").doesNotContain("angel@example.com");
    }

    @Test
    void theLinkIsLoggedOnlyWhenLocalDevelopmentAsksForIt() {
        runner.withPropertyValues("flexbuddy.mail.log-links=true").run(context ->
                context.getBean(PasswordResetMailer.class).sendResetLink("angel@example.com", "Angel", LINK));

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).contains(LINK);
    }

    @Test
    void theSmtpMailerSendsAPlainTextMessageWithTheLink() {
        JavaMailSender sender = mock(JavaMailSender.class);
        new SmtpPasswordResetMailer(sender, "FlexBuddy <flexbuddysupport@gmail.com>")
                .sendResetLink("angel@example.com", "Angel", LINK);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("angel@example.com");
        assertThat(message.getFrom()).isEqualTo("FlexBuddy <flexbuddysupport@gmail.com>");
        assertThat(message.getSubject()).isEqualTo("Reset your FlexBuddy password");
        assertThat(message.getText()).contains("Hi Angel,").contains(LINK).contains("within 30 minutes")
                .contains("If you didn't ask for this");
    }

    @Test
    void aFailedSendIsLoggedWithoutTheAddressOrTheLinkAndNeverThrown() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 mailbox angel@example.com unavailable")).when(sender).send(any(SimpleMailMessage.class));

        new SmtpPasswordResetMailer(sender, "FlexBuddy <flexbuddysupport@gmail.com>")
                .sendResetLink("angel@example.com", "Angel", LINK);

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).contains("could not be sent")
                .doesNotContain("angel@example.com").doesNotContain("SECRET-TOKEN-VALUE");
    }
}
