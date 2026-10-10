package com.angel.flexbuddy.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TwoFactorSecretCipherTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    @Test
    void aSecretSurvivesTheRoundTrip() {
        TwoFactorSecretCipher cipher = new TwoFactorSecretCipher("a-long-random-test-key");

        assertThat(cipher.decrypt(cipher.encrypt(SECRET))).isEqualTo(SECRET);
    }

    @Test
    void theSameSecretEncryptsDifferentlyEachTimeAndNeverShowsThroughTheCiphertext() {
        TwoFactorSecretCipher cipher = new TwoFactorSecretCipher("a-long-random-test-key");

        String first = cipher.encrypt(SECRET);
        String second = cipher.encrypt(SECRET);

        assertThat(first).isNotEqualTo(second).doesNotContain(SECRET);
        assertThat(first.length()).isLessThanOrEqualTo(255);
    }

    @Test
    void anotherKeyCannotReadIt() {
        String stored = new TwoFactorSecretCipher("one-key").encrypt(SECRET);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TwoFactorSecretCipher("another-key").decrypt(stored))
                .isInstanceOf(RuntimeException.class);
    }
}
