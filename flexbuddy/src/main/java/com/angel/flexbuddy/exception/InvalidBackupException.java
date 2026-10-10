package com.angel.flexbuddy.exception;

public class InvalidBackupException extends LocalizedException {

    public InvalidBackupException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }

    public InvalidBackupException(Throwable cause, String messageKey, Object... messageArgs) {
        super(cause, messageKey, messageArgs);
    }
}
