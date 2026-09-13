package com.angel.flexbuddy.exception;

public class InvalidAccountPasswordException extends RuntimeException {

    public InvalidAccountPasswordException() {
        super("The password you entered is incorrect.");
    }
}
