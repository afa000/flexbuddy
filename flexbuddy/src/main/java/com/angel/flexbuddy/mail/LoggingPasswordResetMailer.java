package com.angel.flexbuddy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no mail server is configured, so the app still starts and a reset request still completes. It logs that
 * mail is not set up. It logs the link itself only when {@code flexbuddy.mail.log-links} is true, a setting meant for
 * local development: a production log must never hold a working link.
 */
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailer.class);

    private final boolean logLinks;

    public LoggingPasswordResetMailer(boolean logLinks) {
        this.logLinks = logLinks;
    }

    @Override
    public void sendResetLink(String toEmail, String displayName, String link) {
        if (logLinks) {
            log.info("password reset requested; mail is not configured. Link: {}", link);
        } else {
            log.info("password reset requested; mail is not configured");
        }
    }
}
