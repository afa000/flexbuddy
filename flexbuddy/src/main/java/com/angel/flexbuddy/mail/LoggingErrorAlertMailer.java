package com.angel.flexbuddy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no mail server is configured: the alert's subject goes to the log at INFO and nothing is sent. */
public class LoggingErrorAlertMailer implements ErrorAlertMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingErrorAlertMailer.class);

    @Override
    public void send(String subject, String body) {
        log.info("error alert (mail not configured): {}", subject);
    }
}
