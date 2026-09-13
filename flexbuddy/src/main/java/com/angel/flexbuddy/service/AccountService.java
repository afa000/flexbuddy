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
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.PushSubscriptionRepository;
import com.angel.flexbuddy.repository.ReminderLogRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class AccountService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExpenseRepository expenseRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final ShiftRepository shiftRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PersistentTokenRepository persistentTokenRepository;

    public AccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
            ExpenseRepository expenseRepository, ReminderLogRepository reminderLogRepository,
            ShiftRepository shiftRepository, PushSubscriptionRepository pushSubscriptionRepository,
            PersistentTokenRepository persistentTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.expenseRepository = expenseRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.shiftRepository = shiftRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.persistentTokenRepository = persistentTokenRepository;
    }

    public boolean emailIsRegistered(String email) {
        return email != null && userRepository.existsByEmailIgnoreCase(email.trim());
    }

    @Transactional
    public AppUser register(RegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AppUser user = new AppUser(
                request.getDisplayName().trim(),
                email,
                passwordEncoder.encode(request.getPassword())
        );
        return userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(String email, String password) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("The signed-in account no longer exists."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidAccountPasswordException();
        }

        Long ownerId = user.getId();
        expenseRepository.deleteAllByOwnerIdIncludingTrash(ownerId);
        reminderLogRepository.deleteAllByOwnerId(ownerId);
        shiftRepository.deleteAllByOwnerIdIncludingTrash(ownerId);
        pushSubscriptionRepository.deleteAllByOwnerId(ownerId);
        persistentTokenRepository.removeUserTokens(user.getEmail());
        userRepository.deleteAccountById(ownerId);
    }
}
