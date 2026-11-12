package com.angel.flexbuddy.api;

import java.time.*;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.angel.flexbuddy.model.*;
import com.angel.flexbuddy.repository.*;

@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class AppTokenService {
    private final AppTokenRepository tokens;
    private final AppSignInChallengeRepository challenges;
    private final Clock clock;
    public AppTokenService(AppTokenRepository tokens, AppSignInChallengeRepository challenges, Clock clock) {
        this.tokens = tokens;
        this.challenges = challenges;
        this.clock = clock;
    }
    // Called only while holding the owner's row lock in ApiSignInService.
    String issue(AppUser owner, String deviceName) {
        String secret = ApiSecrets.create("fbt_");
        Instant now = clock.instant();
        AppToken token = new AppToken();
        token.setOwner(owner);
        token.setDeviceName(deviceName);
        token.setTokenHash(ApiSecrets.hash(secret));
        token.setCreatedAt(now);
        token.setLastUsedAt(now);
        token.setExpiresAt(now.plus(Duration.ofDays(30)));
        tokens.save(token);
        return secret;
    }
    @Transactional
    public Optional<Identity> authenticate(String secret) {
        if (!ApiSecrets.valid(secret, "fbt_")) return Optional.empty();
        return tokens.findByTokenHash(ApiSecrets.hash(secret))
                .filter(t -> t.getExpiresAt().isAfter(clock.instant()))
                .map(t -> {
                    Instant now = clock.instant();
                    tokens.touch(t.getId(), now, now.plus(Duration.ofDays(30)), now.minus(Duration.ofHours(1)));
                    return new Identity(t.getId(), t.getOwner().getEmail());
                });
    }
    public record Identity(Long tokenId, String email) { }
    @Transactional
    public void signOut(Long tokenId) { tokens.deleteById(tokenId); }
    @Scheduled(cron = "0 15 5 * * *")
    @Transactional
    public void purgeExpired() {
        Instant now = clock.instant();
        tokens.deleteExpired(now);
        challenges.deleteExpired(now);
    }
}
