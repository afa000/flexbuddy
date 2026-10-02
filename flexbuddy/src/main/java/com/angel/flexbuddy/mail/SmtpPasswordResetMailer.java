package com.angel.flexbuddy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/**
 * Sends the reset email over SMTP as plain text, which delivers more reliably than HTML. It runs on the mail executor
 * so the visitor's page returns straight away whether or not an email goes out. A failure is logged without the
 * address or the link and is never shown to the visitor, whose page looks the same either way.
 */
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);

    private final JavaMailSender sender;
    private final String from;

    public SmtpPasswordResetMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    @Async("mailExecutor")
    public void sendResetLink(String toEmail, String displayName, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Reset your FlexBuddy password");
        message.setText("""
                Hi %s,

                Someone asked to reset the password for your FlexBuddy account. To choose a new
                password, open this link within 30 minutes:

                %s

                If you didn't ask for this, you can ignore this email. Your password won't change.

                FlexBuddy
                """.formatted(displayName, link));
        try {
            sender.send(message);
        } catch (RuntimeException exception) {
            // Only the kind of failure is logged: a mail exception's message can carry the address.
            log.warn("password reset email could not be sent ({})", exception.getClass().getSimpleName());
        }
    }
}
