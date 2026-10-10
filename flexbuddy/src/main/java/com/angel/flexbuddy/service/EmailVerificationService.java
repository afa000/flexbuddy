package com.angel.flexbuddy.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.EmailCodePurpose;
import com.angel.flexbuddy.repository.AppUserRepository;

/** Confirms that a new sign-up owns its email address, and clears away sign-ups that never did. */
@Service
public class EmailVerificationService {

    private static final Duration ABANDONED_AFTER = Duration.ofHours(24);

    private final EmailCodeService codes;
    private final AppUserRepository userRepository;
    private final AccountService accountService;
    private final Clock clock;

    public EmailVerificationService(EmailCodeService codes, AppUserRepository userRepository,
            AccountService accountService, Clock clock) {
        this.codes = codes;
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.clock = clock;
    }

    /** Emails a fresh code. The result lets the page say so when too many were asked for. */
    public EmailCodeService.SendResult startVerification(AppUser user, String ip) {
        return codes.send(user, EmailCodePurpose.VERIFY_EMAIL, ip);
    }

    /** Checks the code and, when it is right, marks the account verified. */
    @Transactional
    public EmailCodeService.CheckResult verify(Long userId, String code) {
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return EmailCodeService.CheckResult.EXPIRED;
        }
        EmailCodeService.CheckResult result = codes.check(user, EmailCodePurpose.VERIFY_EMAIL, code);
        if (result == EmailCodeService.CheckResult.OK) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        return result;
    }

    /** Deletes sign-ups whose code was never entered within a day. They hold no data, but the shared path stays safe. */
    @Scheduled(cron = "0 55 4 * * *")
    @Transactional
    public void purgeAbandoned() {
        List<AppUser> abandoned = userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(
                clock.instant().minus(ABANDONED_AFTER));
        for (AppUser user : abandoned) {
            accountService.deleteAccountData(user.getId(), user.getEmail());
        }
    }
}
