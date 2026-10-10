package com.angel.flexbuddy.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.exception.InvalidAccountPasswordException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.EmailCodeRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.PasswordResetTokenRepository;
import com.angel.flexbuddy.repository.PushSubscriptionRepository;
import com.angel.flexbuddy.repository.ReminderLogRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.repository.TaxPaymentRepository;

@Service
public class AccountService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExpenseRepository expenseRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final ShiftRepository shiftRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PersistentTokenRepository persistentTokenRepository;
    private final TaxPaymentRepository taxPaymentRepository;
    private final com.angel.flexbuddy.repository.PayoutDepositRepository payoutDepositRepository;
    private final com.angel.flexbuddy.repository.StandingEntryRepository standingEntryRepository;
    private final com.angel.flexbuddy.repository.TaxReminderLogRepository taxReminderLogRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailCodeRepository emailCodeRepository;

    public AccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
            ExpenseRepository expenseRepository, ReminderLogRepository reminderLogRepository,
            ShiftRepository shiftRepository, PushSubscriptionRepository pushSubscriptionRepository,
            PersistentTokenRepository persistentTokenRepository, TaxPaymentRepository taxPaymentRepository,
            com.angel.flexbuddy.repository.PayoutDepositRepository payoutDepositRepository,
            com.angel.flexbuddy.repository.StandingEntryRepository standingEntryRepository,
            com.angel.flexbuddy.repository.TaxReminderLogRepository taxReminderLogRepository,
            PasswordResetTokenRepository passwordResetTokenRepository, EmailCodeRepository emailCodeRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.expenseRepository = expenseRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.shiftRepository = shiftRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.persistentTokenRepository = persistentTokenRepository;
        this.taxPaymentRepository = taxPaymentRepository;
        this.payoutDepositRepository = payoutDepositRepository;
        this.standingEntryRepository = standingEntryRepository;
        this.taxReminderLogRepository = taxReminderLogRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailCodeRepository = emailCodeRepository;
    }

    /** True only for an address whose owner has entered the emailed code; an unconfirmed sign-up does not hold it. */
    public boolean emailIsRegistered(String email) {
        return email != null && userRepository.existsByEmailIgnoreCaseAndEmailVerifiedTrue(email.trim());
    }

    /**
     * Creates the account, not yet verified. When the address only has an unconfirmed sign-up, that attempt is
     * replaced instead, so nobody can keep someone else's address from them by registering it first.
     */
    @Transactional
    public AppUser register(RegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String displayName = request.getDisplayName().trim();
        String passwordHash = passwordEncoder.encode(request.getPassword());
        AppUser existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && !existing.isEmailVerified()) {
            existing.setDisplayName(displayName);
            existing.setPasswordHash(passwordHash);
            return userRepository.save(existing);
        }
        AppUser user = new AppUser(displayName, email, passwordHash);
        user.setEmailVerified(false);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(String email, String password) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("The signed-in account no longer exists."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidAccountPasswordException();
        }

        deleteAccountData(user.getId(), user.getEmail());
    }

    /** Removes everything an account owns, then the account, in the one order that satisfies every reference. */
    @Transactional
    void deleteAccountData(Long ownerId, String email) {
        expenseRepository.deleteAllByOwnerIdIncludingTrash(ownerId);
        taxPaymentRepository.deleteAllByOwnerId(ownerId);
        payoutDepositRepository.deleteAllByOwnerId(ownerId);
        standingEntryRepository.deleteAllByOwnerId(ownerId);
        taxReminderLogRepository.deleteAllByOwnerId(ownerId);
        reminderLogRepository.deleteAllByOwnerId(ownerId);
        shiftRepository.deleteAllByOwnerIdIncludingTrash(ownerId);
        pushSubscriptionRepository.deleteAllByOwnerId(ownerId);
        persistentTokenRepository.removeUserTokens(email);
        passwordResetTokenRepository.deleteAllByOwnerId(ownerId);
        emailCodeRepository.deleteAllByOwnerId(ownerId);
        userRepository.deleteAccountById(ownerId);
    }
}
