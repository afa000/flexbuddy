package com.angel.flexbuddy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts authenticator-app secrets at rest with AES-256-GCM, keyed from the environment, so a copy of the database
 * alone cannot be used to generate sign-in codes. Each encryption uses a fresh random IV, so the same secret never
 * encrypts to the same text twice. Changing the key makes every stored secret unreadable.
 */
@Component
public class TwoFactorSecretCipher {

    /** Not secret: with a secret key it only has to be fixed. */
    private static final String SALT = "6b4f1c9a2d7e83055a1c9e4b7d2f8a36";

    private final TextEncryptor encryptor;

    public TwoFactorSecretCipher(@Value("${flexbuddy.security.two-factor-key}") String key) {
        this.encryptor = Encryptors.delux(key, SALT);
    }

    public String encrypt(String base32Secret) {
        return encryptor.encrypt(base32Secret);
    }

    public String decrypt(String stored) {
        return encryptor.decrypt(stored);
    }
}
