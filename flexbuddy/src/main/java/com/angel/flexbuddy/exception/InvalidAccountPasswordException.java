package com.angel.flexbuddy.exception;

public class InvalidAccountPasswordException extends LocalizedException {

    public InvalidAccountPasswordException() {
        super("error.account.wrongPassword", new Object[0]);
    }
}
