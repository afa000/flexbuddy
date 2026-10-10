package com.angel.flexbuddy.exception;

import com.angel.flexbuddy.i18n.Messages;

/**
 * Base of the exceptions a driver reads. {@code getMessage()} stays English so logs and tests read the same whatever
 * the driver's language; the handler asks {@link LocalizedMessage} for the driver's text.
 */
public abstract class LocalizedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final transient Object[] messageArgs;

    protected LocalizedException(String messageKey, Object[] messageArgs) {
        super(Messages.english(messageKey, messageArgs));
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    protected LocalizedException(Throwable cause, String messageKey, Object[] messageArgs) {
        super(Messages.english(messageKey, messageArgs), cause);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
