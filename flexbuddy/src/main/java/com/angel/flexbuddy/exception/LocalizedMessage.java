package com.angel.flexbuddy.exception;

import java.util.Locale;

import org.springframework.context.MessageSource;

/** An exception whose text a driver reads: the key and arguments let it be shown in the driver's language. */
public interface LocalizedMessage {

    String messageKey();

    Object[] messageArgs();

    /** The text for a driver reading {@code locale}; any other exception keeps its own message. */
    static String text(Throwable exception, MessageSource messages, Locale locale) {
        return exception instanceof LocalizedMessage localized
                ? messages.getMessage(localized.messageKey(), localized.messageArgs(), locale)
                : exception.getMessage();
    }
}
