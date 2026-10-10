package com.angel.flexbuddy.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.angel.flexbuddy.model.EmailCodePurpose;

/** The emails read word for word as they did before their text moved into the message file. */
class EmailTextTest {

    private static SimpleMailMessage sentBy(JavaMailSender sender) {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        return sent.getValue();
    }

    @Test
    void theResetEmailReadsAsBefore() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new SmtpPasswordResetMailer(sender, "from@example.com")
                .sendResetLink("angel@example.com", "Angel", "https://flexbuddy.test/reset-password?token=abc");

        SimpleMailMessage message = sentBy(sender);
        assertThat(message.getSubject()).isEqualTo("Reset your FlexBuddy password");
        assertThat(message.getText()).isEqualTo("""
                Hi Angel,

                Someone asked to reset the password for your FlexBuddy account. To choose a new
                password, open this link within 30 minutes:

                https://flexbuddy.test/reset-password?token=abc

                If you didn't ask for this, you can ignore this email. Your password won't change.

                FlexBuddy
                """);
    }

    @Test
    void theCodeEmailReadsAsBefore() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new SmtpEmailCodeMailer(sender, "from@example.com")
                .sendCode("angel@example.com", "Angel", "493817", EmailCodePurpose.VERIFY_EMAIL);

        SimpleMailMessage message = sentBy(sender);
        assertThat(message.getSubject()).isEqualTo("Your FlexBuddy code: 493817");
        assertThat(message.getText()).isEqualTo("""
                Hi Angel,

                Enter this code in FlexBuddy to confirm your email:

                493817

                It works for 15 minutes. If you didn't create a FlexBuddy account, you can ignore this email.

                FlexBuddy
                """);
    }

    @Test
    void aNameWithBracesOrApostrophesIsNotTreatedAsAPlaceholder() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new SmtpPasswordResetMailer(sender, "from@example.com")
                .sendResetLink("angel@example.com", "O'Neil {0}", "https://flexbuddy.test/r");

        assertThat(sentBy(sender).getText()).startsWith("Hi O'Neil {0},\n");
    }
}
