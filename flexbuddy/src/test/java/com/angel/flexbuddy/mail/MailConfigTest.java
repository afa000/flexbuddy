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
    void theCodeMailerFollowsTheSameHostRule() {
        runner.run(context -> assertThat(context.getBean(EmailCodeMailer.class)).isInstanceOf(LoggingEmailCodeMailer.class));
        runner.withPropertyValues("spring.mail.host=smtp.example.com")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context.getBean(EmailCodeMailer.class)).isInstanceOf(SmtpEmailCodeMailer.class));
    }

    @Test
    void aProductionLogNeverHoldsACode() {
        runner.withPropertyValues("flexbuddy.mail.log-links=false").run(context ->
                context.getBean(EmailCodeMailer.class).sendCode("angel@example.com", "Angel", "493817",
                        com.angel.flexbuddy.model.EmailCodePurpose.VERIFY_EMAIL));

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).isEqualTo("email code not sent: mail is not configured");
    }

    @Test
    void aCodeIsLoggedOnlyWhenLocalDevelopmentAsksForIt() {
        runner.withPropertyValues("flexbuddy.mail.log-links=true").run(context ->
                context.getBean(EmailCodeMailer.class).sendCode("angel@example.com", "Angel", "493817",
                        com.angel.flexbuddy.model.EmailCodePurpose.VERIFY_EMAIL));

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).contains("493817").doesNotContain("angel@example.com");
    }

    @Test
    void theCodeEmailPutsTheCodeInTheSubjectAndNeverLogsItOnFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        new SmtpEmailCodeMailer(sender, "FlexBuddy <flexbuddysupport@gmail.com>")
                .sendCode("angel@example.com", "Angel", "493817", com.angel.flexbuddy.model.EmailCodePurpose.VERIFY_EMAIL);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("Your FlexBuddy code: 493817");
        assertThat(sent.getValue().getTo()).containsExactly("angel@example.com");

        doThrow(new MailSendException("550 mailbox angel@example.com unavailable 493817")).when(sender).send(any(SimpleMailMessage.class));
        new SmtpEmailCodeMailer(sender, "FlexBuddy <flexbuddysupport@gmail.com>")
                .sendCode("angel@example.com", "Angel", "493817", com.angel.flexbuddy.model.EmailCodePurpose.VERIFY_EMAIL);
        assertThat(messages()).anyMatch(message -> message.contains("MailSendException"))
                .noneMatch(message -> message.contains("493817") || message.contains("angel@example.com"));
    }

    @Test
    void theAlertMailerFollowsTheSameHostRule() {
        runner.run(context -> assertThat(context.getBean(ErrorAlertMailer.class)).isInstanceOf(LoggingErrorAlertMailer.class));
        runner.withPropertyValues("spring.mail.host=smtp.example.com")
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context.getBean(ErrorAlertMailer.class)).isInstanceOf(SmtpErrorAlertMailer.class));
    }

    @Test
    void theAlertMailerSendsToTheConfiguredAddress() {
        JavaMailSender sender = mock(JavaMailSender.class);
        new SmtpErrorAlertMailer(sender, "FlexBuddy <flexbuddysupport@gmail.com>", "ops@example.com")
                .send("[FlexBuddy] Server error: X at Y", "body text");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("ops@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("[FlexBuddy] Server error: X at Y");
        assertThat(sent.getValue().getText()).isEqualTo("body text");
    }

    @Test
    void aFailedAlertSendLogsOneWarnWithTheClassNameAndNotTheAddress() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 mailbox ops@example.com unavailable")).when(sender).send(any(SimpleMailMessage.class));

        new SmtpErrorAlertMailer(sender, "FlexBuddy <flexbuddysupport@gmail.com>", "ops@example.com").send("subject", "body");

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).contains("could not be sent").contains("MailSendException").doesNotContain("ops@example.com");
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
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
