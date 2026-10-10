package com.angel.flexbuddy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

import com.angel.flexbuddy.i18n.Messages;
import com.angel.flexbuddy.model.EmailCodePurpose;

/**
 * Sends the code over SMTP as plain text. The code is in the subject so it shows in a phone's notification. It runs on
 * the mail executor so the page returns straight away, and a failure is logged without the address or the code.
 */
public class SmtpEmailCodeMailer implements EmailCodeMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailCodeMailer.class);

    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailCodeMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    @Async("mailExecutor")
    public void sendCode(String toEmail, String displayName, String code, EmailCodePurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(Messages.english("email.code.subject", code));
        message.setText(Messages.english("email.code.body", displayName, code));
        try {
            sender.send(message);
        } catch (RuntimeException exception) {
            // Only the kind of failure is logged: a mail exception's message can carry the address.
            log.warn("email code could not be sent ({})", exception.getClass().getSimpleName());
        }
    }
}
