package com.angel.flexbuddy.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.angel.flexbuddy.repository.*;

/** Always active: resetting a password must revoke credentials even with the API disabled. */
@Service
public class AppCredentialRevoker {
    private final AppUserRepository users;
    private final AppTokenRepository tokens;
    private final AppSignInChallengeRepository challenges;

    public AppCredentialRevoker(AppUserRepository users, AppTokenRepository tokens,
            AppSignInChallengeRepository challenges) {
        this.users = users;
        this.tokens = tokens;
        this.challenges = challenges;
    }

    @Transactional
    public void revoke(Long ownerId) {
        users.lockById(ownerId);
        tokens.deleteAllByOwnerId(ownerId);
        challenges.deleteAllByOwnerId(ownerId);
    }
}
