package com.angel.flexbuddy.controller;

import java.util.Locale;
import java.util.regex.Pattern;

import com.angel.flexbuddy.exception.InvalidRequestIdException;

/** Reads the optional Idempotency-Key header, which must be a UUID. */
final class RequestIds {

    static final String HEADER = "Idempotency-Key";
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private RequestIds() {
    }

    /** Null or blank is no key; a UUID comes back lower-cased; anything else is rejected. */
    static String parse(String header) {
        if (header == null || header.isBlank()) return null;
        String value = header.trim();
        if (!UUID.matcher(value).matches()) throw new InvalidRequestIdException();
        return value.toLowerCase(Locale.ROOT);
    }
}
