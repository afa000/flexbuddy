package com.angel.flexbuddy.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.i18n.UserLocales;
import com.angel.flexbuddy.mail.EmailCodeMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.EmailCode;
import com.angel.flexbuddy.model.EmailCodePurpose;
import com.angel.flexbuddy.repository.EmailCodeRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.SecurityLimitsProperties;

/**
 * Emailed six-digit codes, shared by every flow that needs one. A code is stored only as a keyed hash: a six-digit code
 * has just a million values, so a plain hash could be reversed from a leaked database in seconds, and the key stops that.
 * A code works once, expires, allows a few wrong tries, and sending another cancels the earlier one. Sends are limited
 * per address and per connection.
 */
@Service
public class EmailCodeService {

    public enum SendResult { SENT, LIMITED }

    public enum CheckResult { OK, WRONG, EXPIRED, TOO_MANY }

    private final EmailCodeRepository codeRepository;
    private final EmailCodeMailer mailer;
    private final AttemptLimiter limiter;
    private final SecurityLimitsProperties.Codes limits;
    private final Clock clock;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public EmailCodeService(EmailCodeRepository codeRepository, EmailCodeMailer mailer, AttemptLimiter limiter,
            SecurityLimitsProperties limits, Clock clock,
            @Value("${flexbuddy.security.remember-me-key}") String secret) {
        this.codeRepository = codeRepository;
        this.mailer = mailer;
        this.limiter = limiter;
        this.limits = limits.codes();
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Emails a new code, unless this address or connection has asked for too many; nothing is sent then. */
    @Transactional
    public SendResult send(AppUser user, EmailCodePurpose purpose, String ip) {
        String email = AttemptLimiter.emailKey(user.getEmail());
        if (limiter.isLocked(AttemptLimiter.CODE_IP, ip) || limiter.isLocked(AttemptLimiter.CODE_EMAIL, email)) {
            return SendResult.LIMITED;
        }
        limiter.record(AttemptLimiter.CODE_IP, ip);
        limiter.record(AttemptLimiter.CODE_EMAIL, email);

        Instant now = clock.instant();
        codeRepository.markUnusedAsUsed(user.getId(), purpose, now);
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailCode stored = new EmailCode();
        stored.setOwner(user);
        stored.setPurpose(purpose);
        stored.setCodeHash(hash(user.getId(), purpose, code));
        stored.setCreatedAt(now);
        stored.setExpiresAt(now.plus(limits.validFor()));
        codeRepository.save(stored);

        mailer.sendCode(user.getEmail(), user.getDisplayName(), code, purpose, UserLocales.of(user));
        return SendResult.SENT;
    }

    /** Checks what the driver typed against the latest code; a right code is used up, a wrong one counts a try. */
    @Transactional
    public CheckResult check(AppUser user, EmailCodePurpose purpose, String entered) {
        String digits = entered == null ? "" : entered.replaceAll("\\s", "");
        // Anything that is not six digits cannot be a code, so it is not worth a try.
        if (!digits.matches("\\d{6}")) {
            return CheckResult.WRONG;
        }
        Optional<EmailCode> latest = codeRepository
                .findFirstByOwnerIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose);
        Instant now = clock.instant();
        if (latest.isEmpty() || !latest.get().getExpiresAt().isAfter(now)) {
            return CheckResult.EXPIRED;
        }
        EmailCode code = latest.get();
        if (code.getAttempts() >= limits.maxAttempts()) {
            return CheckResult.TOO_MANY;
        }
        boolean match = MessageDigest.isEqual(
                code.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(user.getId(), purpose, digits).getBytes(StandardCharsets.UTF_8));
        if (match) {
            code.setUsedAt(now);
            return CheckResult.OK;
        }
        code.setAttempts(code.getAttempts() + 1);
        return code.getAttempts() >= limits.maxAttempts() ? CheckResult.TOO_MANY : CheckResult.WRONG;
    }

    /** Removes codes a day after they expired. */
    @Scheduled(cron = "0 50 4 * * *")
    @Transactional
    public void purgeExpired() {
        codeRepository.deleteByExpiresAtBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private String hash(Long ownerId, EmailCodePurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((ownerId + ":" + purpose + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is always available", exception);
        }
    }
}
