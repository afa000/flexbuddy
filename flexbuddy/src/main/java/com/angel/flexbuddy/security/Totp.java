package com.angel.flexbuddy.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords as RFC 6238 defines them: HMAC-SHA1, thirty-second steps and six digits, which is what
 * every authenticator app uses by default. Pure functions, so they run without a server.
 */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** The six-digit code for one time step. */
    public static String code(byte[] key, long timeStep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timeStep).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA1 is always available", exception);
        }
    }

    /**
     * The step a typed code belongs to, trying the previous, current and next step so a phone clock a little off still
     * works. Empty when the code matches none of them. The comparison does not stop at the first different digit.
     */
    public static OptionalLong matchStep(byte[] key, String entered, long nowStep) {
        if (entered == null || !entered.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        byte[] typed = entered.getBytes(StandardCharsets.UTF_8);
        OptionalLong found = OptionalLong.empty();
        for (long step = nowStep - 1; step <= nowStep + 1; step++) {
            if (MessageDigest.isEqual(typed, code(key, step).getBytes(StandardCharsets.UTF_8))) {
                found = OptionalLong.of(step);
            }
        }
        return found;
    }

    /** Twenty random bytes, the size RFC 4226 recommends for an HMAC-SHA1 key. */
    public static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** RFC 4648 base32 without padding, the form authenticator apps take as a setup key. */
    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    /** Reads base32 back into bytes, ignoring spaces, dashes, padding and case, as a typed setup key may have them. */
    public static byte[] fromBase32(String text) {
        String clean = text.toUpperCase(Locale.ROOT).replaceAll("[\\s=-]", "");
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8 + 1);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        byte[] result = new byte[out.position()];
        out.flip();
        out.get(result);
        return result;
    }

    /** The address an authenticator app opens or a QR code carries to set a key up. */
    public static String otpauthUri(String base32Secret, String email) {
        return "otpauth://totp/FlexBuddy:" + URLEncoder.encode(email, StandardCharsets.UTF_8).replace("+", "%20")
                + "?secret=" + base32Secret + "&issuer=FlexBuddy&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
    }
}
