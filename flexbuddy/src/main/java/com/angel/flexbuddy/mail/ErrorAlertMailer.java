package com.angel.flexbuddy.mail;

/** Sends an alert email to the operator. */
public interface ErrorAlertMailer {

    void send(String subject, String body);
}
