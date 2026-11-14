package com.angel.flexbuddy.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * The opaque sync cursor: the latest change on the account the phone has seen. It is a time, not a secret, and it
 * never chooses an account or a record; the version prefix lets the format change later.
 */
final class SyncCursor {
    private static final String VERSION = "v1:";

    private SyncCursor() {
    }

    static String encode(Instant instant) {
        String plain = VERSION + instant.getEpochSecond() + ":" + instant.getNano();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.US_ASCII));
    }

    /** A cursor this server didn't write; the API answers 400 {@code invalid}. */
    static final class InvalidCursorException extends RuntimeException {
        InvalidCursorException(Throwable cause) {
            super("The sync cursor could not be read.", cause);
        }
    }

    /** The cursor's time, or an {@link InvalidCursorException} for anything this server didn't write. */
    static Instant decode(String cursor) {
        if (cursor == null || cursor.isEmpty() || cursor.length() > 64) throw new InvalidCursorException(null);
        try {
            String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            if (!plain.startsWith(VERSION)) throw new IllegalArgumentException("cursor");
            String[] parts = plain.substring(VERSION.length()).split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("cursor");
            long seconds = Long.parseLong(parts[0]);
            int nanos = Integer.parseInt(parts[1]);
            if (nanos < 0 || nanos > 999_999_999) throw new IllegalArgumentException("cursor");
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (RuntimeException exception) {
            throw new InvalidCursorException(exception);
        }
    }
}
