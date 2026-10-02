package com.angel.flexbuddy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/**
 * Sends an alert over SMTP on the mail executor, so the code that logged the error never waits for Gmail. A failure
 * is logged as a WARN with only the class name, which keeps it out of the alerts themselves.
 */
public class SmtpErrorAlertMailer implements ErrorAlertMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpErrorAlertMailer.class);

    private final JavaMailSender sender;
    private final String from;
    private final String to;

    public SmtpErrorAlertMailer(JavaMailSender sender, String from, String to) {
        this.sender = sender;
        this.from = from;
        this.to = to;
    }

    @Override
    @Async("mailExecutor")
    public void send(String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (RuntimeException exception) {
            log.warn("error alert email could not be sent ({})", exception.getClass().getSimpleName());
        }
    }
}
