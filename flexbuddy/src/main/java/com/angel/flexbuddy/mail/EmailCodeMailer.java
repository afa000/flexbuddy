package com.angel.flexbuddy.mail;

import com.angel.flexbuddy.model.EmailCodePurpose;

/** Sends the six-digit code that proves a driver owns an email address. */
public interface EmailCodeMailer {

    void sendCode(String toEmail, String displayName, String code, EmailCodePurpose purpose);
}
