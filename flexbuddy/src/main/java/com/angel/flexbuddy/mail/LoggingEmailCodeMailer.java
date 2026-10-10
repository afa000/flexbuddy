package com.angel.flexbuddy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.angel.flexbuddy.model.EmailCodePurpose;

/**
 * Used when no mail server is configured. It logs the code only when {@code flexbuddy.mail.log-links} is true, a
 * setting meant for local development: a production log must never hold a working code.
 */
public class LoggingEmailCodeMailer implements EmailCodeMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailCodeMailer.class);

    private final boolean logCodes;

    public LoggingEmailCodeMailer(boolean logCodes) {
        this.logCodes = logCodes;
    }

    @Override
    public void sendCode(String toEmail, String displayName, String code, EmailCodePurpose purpose) {
        if (logCodes) {
            log.info("email code requested; mail is not configured. Code: {}", code);
        } else {
            log.info("email code not sent: mail is not configured");
        }
    }
}
