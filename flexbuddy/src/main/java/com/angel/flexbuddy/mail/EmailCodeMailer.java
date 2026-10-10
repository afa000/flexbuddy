package com.angel.flexbuddy.mail;

import java.util.Locale;

import com.angel.flexbuddy.model.EmailCodePurpose;

/** Sends the six-digit code that proves a driver owns an email address. */
public interface EmailCodeMailer {

    /** The email in the driver's language; a mailer that has no wording for it sends the English one. */
    default void sendCode(String toEmail, String displayName, String code, EmailCodePurpose purpose, Locale locale) {
        sendCode(toEmail, displayName, code, purpose);
    }

    void sendCode(String toEmail, String displayName, String code, EmailCodePurpose purpose);
}
