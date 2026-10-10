package com.angel.flexbuddy.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class TotpTest {

    /** The SHA-1 key from RFC 6238's appendix, and the last six digits of the codes it lists. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void producesTheCodesRfc6238Lists() {
        assertThat(Totp.code(RFC_KEY, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_KEY, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.code(RFC_KEY, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void acceptsThePreviousCurrentAndNextStepButNotTwoAway() {
        long now = 1234567890L / 30;

        assertThat(Totp.matchStep(RFC_KEY, Totp.code(RFC_KEY, now - 1), now)).isEqualTo(OptionalLong.of(now - 1));
        assertThat(Totp.matchStep(RFC_KEY, Totp.code(RFC_KEY, now), now)).isEqualTo(OptionalLong.of(now));
        assertThat(Totp.matchStep(RFC_KEY, Totp.code(RFC_KEY, now + 1), now)).isEqualTo(OptionalLong.of(now + 1));
        assertThat(Totp.matchStep(RFC_KEY, Totp.code(RFC_KEY, now + 2), now)).isEmpty();
        assertThat(Totp.matchStep(RFC_KEY, Totp.code(RFC_KEY, now - 2), now)).isEmpty();
    }

    @Test
    void rejectsAnythingThatIsNotSixDigits() {
        assertThat(Totp.matchStep(RFC_KEY, "12345", 1)).isEmpty();
        assertThat(Totp.matchStep(RFC_KEY, "abcdef", 1)).isEmpty();
        assertThat(Totp.matchStep(RFC_KEY, null, 1)).isEmpty();
        assertThat(Totp.matchStep(RFC_KEY, "1234567", 1)).isEmpty();
    }

    @Test
    void base32RoundTripsRandomKeysAndIgnoresSpacesAndCase() {
        byte[] key = new byte[20];
        new SecureRandom().nextBytes(key);

        String text = Totp.base32(key);

        assertThat(text).matches("[A-Z2-7]{32}");
        assertThat(Totp.fromBase32(text)).isEqualTo(key);
        String typed = (text.substring(0, 4) + " " + text.substring(4, 8) + "-" + text.substring(8)).toLowerCase();
        assertThat(Totp.fromBase32(typed)).isEqualTo(key);
    }

    @Test
    void base32MatchesTheRfc4648Examples() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Totp.fromBase32("MZXW6YTBOI")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void otpauthUriEncodesTheEmailAndNamesTheApp() {
        String uri = Totp.otpauthUri("ABCDEFGH", "a+b@example.com");

        assertThat(uri).isEqualTo("otpauth://totp/FlexBuddy:a%2Bb%40example.com"
                + "?secret=ABCDEFGH&issuer=FlexBuddy&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void aNewSecretIsTwentyRandomBytes() {
        byte[] first = Totp.newSecret();
        byte[] second = Totp.newSecret();

        assertThat(first).hasSize(20);
        assertThat(first).isNotEqualTo(second);
    }
}
