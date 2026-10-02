package com.angel.flexbuddy.mail;

/** Sends the one-time link that lets a driver choose a new password. */
public interface PasswordResetMailer {

    void sendResetLink(String toEmail, String displayName, String link);
}
