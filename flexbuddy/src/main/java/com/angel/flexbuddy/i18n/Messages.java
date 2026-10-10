package com.angel.flexbuddy.i18n;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Text for a message key in {@code i18n/messages*.properties}, for code that has no {@code MessageSource} at hand
 * (services, exceptions, jobs). Pages and form errors go through Spring's own message source instead.
 *
 * <p>Numbers are formatted by {@code MessageFormat}, which adds a thousands separator (10,000). Pass a number that can
 * reach four digits as a string.
 */
public final class Messages {

    private static final ResourceBundleMessageSource SOURCE = source();

    private static final Map<Locale, DateTimeFormatter> DAYS = new ConcurrentHashMap<>();
    private static final Map<Locale, DateTimeFormatter> TIMES = new ConcurrentHashMap<>();

    private Messages() {
    }

    /** A weekday, month and day such as "Mon Sep 28", written the way the language writes it. */
    public static DateTimeFormatter dayFormat(Locale locale) {
        return DAYS.computeIfAbsent(locale, l -> DateTimeFormatter.ofPattern(Messages.in(l, "format.day"), l));
    }

    /** A clock time such as "9:00 AM", written the way the language writes it. */
    public static DateTimeFormatter timeFormat(Locale locale) {
        return TIMES.computeIfAbsent(locale, l -> DateTimeFormatter.ofPattern(Messages.in(l, "format.time"), l));
    }

    private static ResourceBundleMessageSource source() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    /** English text, for exception messages and logs; they stay English whatever language the driver reads. */
    public static String english(String key, Object... args) {
        return SOURCE.getMessage(key, args, Locale.ENGLISH);
    }

    /** Text in the language of the request being served (English outside a request). */
    public static String current(String key, Object... args) {
        return SOURCE.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /** Text in the given language. */
    public static String in(Locale locale, String key, Object... args) {
        return SOURCE.getMessage(key, args, locale);
    }
}
