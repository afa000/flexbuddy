package com.angel.flexbuddy.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.exception.InvalidResetTokenException;
import com.angel.flexbuddy.mail.PasswordResetMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PasswordResetToken;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PasswordResetTokenRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.SecurityLimitsProperties;

/**
 * The forgotten-password flow. A link is 32 random bytes written as base64url; only its SHA-256 hash is stored. It
 * works once, expires, and is cancelled when another is asked for. Asking is limited per email and per connection, and
 * over a limit nothing is sent while the visitor's page looks exactly the same.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersistentTokenRepository persistentTokenRepository;
    private final AttemptLimiter limiter;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final Duration linkValidFor;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, AppUserRepository userRepository,
            PasswordEncoder passwordEncoder, PersistentTokenRepository persistentTokenRepository,
            AttemptLimiter limiter, PasswordResetMailer mailer, Clock clock, SecurityLimitsProperties limits,
            @Value("${flexbuddy.public-url}") String publicUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.persistentTokenRepository = persistentTokenRepository;
        this.limiter = limiter;
        this.mailer = mailer;
        this.clock = clock;
        this.linkValidFor = limits.reset().linkValidFor();
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /**
     * Emails a reset link when the address has an account and neither limit is reached. It returns nothing and
     * throws nothing the visitor could tell apart, so every request gets the same page.
     */
    @Transactional
    public void requestReset(String rawEmail, String ip) {
        String email = AttemptLimiter.emailKey(rawEmail);
        if (limiter.isLocked(AttemptLimiter.RESET_IP, ip) || limiter.isLocked(AttemptLimiter.RESET_EMAIL, email)) {
            return;
        }
        // An unknown address counts too, so the limits do not give away which emails have accounts.
        limiter.record(AttemptLimiter.RESET_IP, ip);
        limiter.record(AttemptLimiter.RESET_EMAIL, email);

        Optional<AppUser> found = userRepository.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            return;
        }
        AppUser user = found.get();
        Instant now = clock.instant();
        tokenRepository.retireUnused(user.getId(), now);

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken stored = new PasswordResetToken();
        stored.setOwner(user);
        stored.setTokenHash(hash(token));
        stored.setCreatedAt(now);
        stored.setExpiresAt(now.plus(linkValidFor));
        tokenRepository.save(stored);

        mailer.sendResetLink(user.getEmail(), user.getDisplayName(), publicUrl + "/reset-password?token=" + token);
    }

    /** The account a link belongs to, but only while the link is unused and unexpired. */
    @Transactional(readOnly = true)
    public Optional<AppUser> checkToken(String token) {
        return findUsable(token).map(PasswordResetToken::getOwner);
    }

    /**
     * Sets the new password and uses up the link. It also cancels any other links, signs out every device that stayed
     * signed in, and lifts any sign-in lock on the email.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken link = findUsable(token).orElseThrow(InvalidResetTokenException::new);
        Instant now = clock.instant();
        AppUser user = link.getOwner();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // The link went to the address, so using it proves the driver owns it.
        user.setEmailVerified(true);
        // This is also how a driver who signed up with Google adds a password.
        user.setPasswordSet(true);
        link.setUsedAt(now);
        tokenRepository.retireUnused(user.getId(), now);
        persistentTokenRepository.removeUserTokens(user.getEmail());
        limiter.clear(AttemptLimiter.LOGIN_EMAIL, AttemptLimiter.emailKey(user.getEmail()));
    }

    /** Removes links a day after they expired. It lives here, not in the shift purge job, to leave that job alone. */
    @Scheduled(cron = "0 45 4 * * *")
    @Transactional
    public void purgeExpired() {
        tokenRepository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private Optional<PasswordResetToken> findUsable(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokenRepository.findByTokenHash(hash(token))
                .filter(link -> link.getUsedAt() == null && link.getExpiresAt().isAfter(now));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }
}
