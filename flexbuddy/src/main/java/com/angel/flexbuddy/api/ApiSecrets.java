package com.angel.flexbuddy.api;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

final class ApiSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();
    private ApiSecrets() { }
    static String create(String prefix) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static boolean valid(String value, String prefix) {
        return value != null && value.matches(prefix + "[A-Za-z0-9_-]{43}");
    }
    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
