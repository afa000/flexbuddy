package com.angel.flexbuddy.mail;

import java.util.Locale;

/** Sends the one-time link that lets a driver choose a new password. */
public interface PasswordResetMailer {

    /** The email in the driver's language; a mailer that has no wording for it sends the English one. */
    default void sendResetLink(String toEmail, String displayName, String link, Locale locale) {
        sendResetLink(toEmail, displayName, link);
    }

    void sendResetLink(String toEmail, String displayName, String link);
}
