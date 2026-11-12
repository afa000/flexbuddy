package com.angel.flexbuddy.api;

import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.angel.flexbuddy.model.*;
import com.angel.flexbuddy.repository.*;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.service.*;
import jakarta.servlet.http.HttpServletRequest;

@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiSignInService {
    private final AppUserRepository users;
    private final AppSignInChallengeRepository challenges;
    private final AppTokenService tokens;
    private final AttemptLimiter limiter;
    private final EmailVerificationService verification;
    private final TwoFactorService twoFactor;
    private final Clock clock;
    private final ProviderManager passwords;

    public ApiSignInService(AppUserRepository users, AppSignInChallengeRepository challenges, AppTokenService tokens,
            AttemptLimiter limiter, EmailVerificationService verification, TwoFactorService twoFactor, Clock clock,
            UserDetailsService details, PasswordEncoder encoder, ApplicationEventPublisher events) {
        this.users = users;
        this.challenges = challenges;
        this.tokens = tokens;
        this.limiter = limiter;
        this.verification = verification;
        this.twoFactor = twoFactor;
        this.clock = clock;
        var provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        passwords = new ProviderManager(provider);
        passwords.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(events));
    }

    @Transactional
    public ResponseEntity<?> signIn(String rawEmail, String password, String device, HttpServletRequest request) {
        String email = AttemptLimiter.emailKey(rawEmail);
        String ip = request.getRemoteAddr();
        if (locked(email, ip)) return lockedResponse(email, ip);
        // Acquire before password authentication: reset cannot change a checked password before issuance.
        AppUser user = users.lockByEmail(email).orElse(null);
        if (locked(email, ip)) return lockedResponse(email, ip);
        var authentication = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
        authentication.setDetails(new WebAuthenticationDetails(request));
        try {
            passwords.authenticate(authentication);
        } catch (AuthenticationException ignored) {
            // LoginAttemptEvents records the shared email/IP failures exactly once.
            return error(401, "invalid_credentials");
        }
        if (user == null || !user.isPasswordSet()) {
            limiter.record(AttemptLimiter.LOGIN_EMAIL, email);
            limiter.record(AttemptLimiter.LOGIN_IP, ip);
            return error(401, "invalid_credentials");
        }
        challenges.deleteAllByOwnerId(user.getId());
        if (!user.isEmailVerified()) return challenge(user, device, AppSignInChallenge.Purpose.VERIFY_EMAIL, ip);
        if (user.getTwoFactorMethod() != null) return challenge(user, device, AppSignInChallenge.Purpose.TWO_FACTOR, ip);
        return signedIn(user, device, false);
    }

    @Transactional
    public ResponseEntity<?> code(String secret, String code, String ip) {
        AppSignInChallenge challenge = usableChallenge(secret);
        if (challenge == null) return error(401, "challenge_expired");
        AppUser user = challenge.getOwner();
        String email = AttemptLimiter.emailKey(user.getEmail());
        if (locked(email, ip)) {
            challenges.delete(challenge);
            return lockedResponse(email, ip);
        }
        boolean ok;
        boolean expired;
        boolean recovery = false;
        if (challenge.getPurpose() == AppSignInChallenge.Purpose.VERIFY_EMAIL) {
            var result = verification.verify(user.getId(), code);
            ok = result == EmailCodeService.CheckResult.OK;
            expired = result == EmailCodeService.CheckResult.EXPIRED;
            if (!ok) limiter.record(AttemptLimiter.LOGIN_EMAIL, email);
        } else {
            if (!user.isEmailVerified() || user.getTwoFactorMethod() == null) {
                challenges.delete(challenge);
                return error(401, "challenge_expired");
            }
            var result = twoFactor.verifySignIn(user, code);
            ok = result == TwoFactorService.Result.OK || result == TwoFactorService.Result.OK_RECOVERY;
            recovery = result == TwoFactorService.Result.OK_RECOVERY;
            expired = result == TwoFactorService.Result.EXPIRED;
            // TwoFactorService counts WRONG. An expired emailed code also counts for this API.
            if (expired) limiter.record(AttemptLimiter.LOGIN_EMAIL, email);
        }
        if (!ok) {
            if (locked(email, ip)) {
                challenges.delete(challenge);
                return lockedResponse(email, ip);
            }
            return error(400, expired ? "code_expired" : "code_wrong");
        }
        challenges.delete(challenge);
        challenges.flush();
        if (challenge.getPurpose() == AppSignInChallenge.Purpose.VERIFY_EMAIL && user.getTwoFactorMethod() != null) {
            return challenge(user, challenge.getDeviceName(), AppSignInChallenge.Purpose.TWO_FACTOR, ip);
        }
        return signedIn(user, challenge.getDeviceName(), recovery);
    }

    @Transactional
    public ResponseEntity<?> resend(String secret, String ip) {
        AppSignInChallenge challenge = usableChallenge(secret);
        if (challenge == null) return error(401, "challenge_expired");
        AppUser user = challenge.getOwner();
        String email = AttemptLimiter.emailKey(user.getEmail());
        if (locked(email, ip)) {
            challenges.delete(challenge);
            return lockedResponse(email, ip);
        }
        if (challenge.getPurpose() == AppSignInChallenge.Purpose.TWO_FACTOR
                && user.getTwoFactorMethod() != TwoFactorMethod.EMAIL) return error(400, "invalid");
        var result = challenge.getPurpose() == AppSignInChallenge.Purpose.VERIFY_EMAIL
                ? verification.startVerification(user, ip) : twoFactor.sendSignInEmail(user, ip);
        return result == EmailCodeService.SendResult.LIMITED ? sendLimited(email, ip) : ResponseEntity.noContent().build();
    }

    private AppSignInChallenge usableChallenge(String secret) {
        if (!ApiSecrets.valid(secret, "fbc_")) return null;
        String hash = ApiSecrets.hash(secret);
        var found = challenges.findByChallengeHash(hash);
        if (found.isEmpty()) return null;
        // Every consumer/issuer/revoker uses this lock. Re-read after waiting for a competing consumer.
        Long ownerId = found.get().getOwner().getId();
        if (users.lockById(ownerId).isEmpty()) return null;
        var current = challenges.findByChallengeHash(hash);
        if (current.isEmpty()) return null;
        AppSignInChallenge challenge = current.get();
        if (!challenge.getExpiresAt().isAfter(clock.instant())) {
            challenges.delete(challenge);
            return null;
        }
        return challenge;
    }

    private ResponseEntity<?> challenge(AppUser user, String device, AppSignInChallenge.Purpose purpose, String ip) {
        var result = purpose == AppSignInChallenge.Purpose.VERIFY_EMAIL ? verification.startVerification(user, ip)
                : user.getTwoFactorMethod() == TwoFactorMethod.EMAIL ? twoFactor.sendSignInEmail(user, ip)
                : EmailCodeService.SendResult.SENT;
        String email = AttemptLimiter.emailKey(user.getEmail());
        if (result == EmailCodeService.SendResult.LIMITED) return sendLimited(email, ip);
        String secret = ApiSecrets.create("fbc_");
        AppSignInChallenge challenge = new AppSignInChallenge();
        challenge.setOwner(user);
        challenge.setDeviceName(device);
        challenge.setPurpose(purpose);
        challenge.setChallengeHash(ApiSecrets.hash(secret));
        challenge.setCreatedAt(clock.instant());
        challenge.setExpiresAt(clock.instant().plus(Duration.ofMinutes(10)));
        challenges.save(challenge);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "code_required");
        body.put("purpose", purpose == AppSignInChallenge.Purpose.VERIFY_EMAIL ? "verify_email" : "two_factor");
        if (purpose == AppSignInChallenge.Purpose.TWO_FACTOR) body.put("method", user.getTwoFactorMethod().name().toLowerCase(Locale.ROOT));
        body.put("challenge", secret);
        body.put("expiresAt", challenge.getExpiresAt());
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<?> signedIn(AppUser user, String device, boolean recovery) {
        String token = tokens.issue(user, device);
        limiter.clear(AttemptLimiter.LOGIN_EMAIL, AttemptLimiter.emailKey(user.getEmail()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "signed_in");
        body.put("token", token);
        body.put("expiresAt", clock.instant().plus(Duration.ofDays(30)));
        body.put("account", ApiAccount.of(user));
        if (recovery) body.put("recoveryCodeUsed", true);
        return ResponseEntity.ok(body);
    }
    private boolean locked(String email, String ip) {
        return limiter.isLocked(AttemptLimiter.LOGIN_EMAIL, email) || limiter.isLocked(AttemptLimiter.LOGIN_IP, ip);
    }
    private ResponseEntity<?> lockedResponse(String email, String ip) {
        return limited(AttemptLimiter.LOGIN_EMAIL, email, AttemptLimiter.LOGIN_IP, ip);
    }
    private ResponseEntity<?> sendLimited(String email, String ip) {
        return limited(AttemptLimiter.CODE_EMAIL, email, AttemptLimiter.CODE_IP, ip);
    }
    private ResponseEntity<?> limited(String bucket, String key, String otherBucket, String otherKey) {
        Duration duration = limiter.lockedFor(bucket, key).orElse(Duration.ZERO);
        Duration other = limiter.lockedFor(otherBucket, otherKey).orElse(Duration.ZERO);
        long minutes = Math.max(1, (Math.max(duration.toSeconds(), other.toSeconds()) + 59) / 60);
        return ResponseEntity.status(429).body(Map.of("error", "locked", "retryAfterMinutes", minutes));
    }
    private ResponseEntity<?> error(int status, String error) {
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
