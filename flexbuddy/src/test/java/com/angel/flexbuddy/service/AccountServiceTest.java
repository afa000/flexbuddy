package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.exception.InvalidAccountPasswordException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.PushSubscriptionRepository;
import com.angel.flexbuddy.repository.ReminderLogRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ReminderLogRepository reminderLogRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private PersistentTokenRepository persistentTokenRepository;

    @Mock
    private com.angel.flexbuddy.repository.TaxPaymentRepository taxPaymentRepository;

    @Mock
    private com.angel.flexbuddy.repository.PayoutDepositRepository payoutDepositRepository;

    @Mock
    private com.angel.flexbuddy.repository.StandingEntryRepository standingEntryRepository;

    @Mock
    private com.angel.flexbuddy.repository.TaxReminderLogRepository taxReminderLogRepository;

    @Mock
    private com.angel.flexbuddy.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private com.angel.flexbuddy.repository.EmailCodeRepository emailCodeRepository;

    @Mock
    private com.angel.flexbuddy.repository.RecoveryCodeRepository recoveryCodeRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void register_normalizesEmailAndStoresAHashInsteadOfThePassword() {
        RegistrationRequest request = new RegistrationRequest();
        request.setDisplayName("  Angel  ");
        request.setEmail("  ANGEL@Example.COM  ");
        request.setPassword("secret-password");
        when(passwordEncoder.encode("secret-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.register(request);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("Angel");
        assertThat(saved.getEmail()).isEqualTo("angel@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getPasswordHash()).isNotEqualTo(request.getPassword());
    }

    @Test
    void register_savesANewAccountAsNotYetVerified() {
        RegistrationRequest request = new RegistrationRequest();
        request.setDisplayName("Angel");
        request.setEmail("angel@example.com");
        request.setPassword("secret-password");
        when(passwordEncoder.encode("secret-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser saved = accountService.register(request);

        assertThat(saved.isEmailVerified()).isFalse();
    }

    @Test
    void register_replacesAnUnconfirmedSignUpForTheSameAddress() {
        AppUser earlier = new AppUser("Someone Else", "angel@example.com", "old-hash");
        earlier.setId(7L);
        earlier.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(earlier));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RegistrationRequest request = new RegistrationRequest();
        request.setDisplayName("Angel");
        request.setEmail("angel@example.com");
        request.setPassword("new-password");

        AppUser saved = accountService.register(request);

        // The same row is reused, so one address never has two accounts.
        assertThat(saved).isSameAs(earlier);
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getDisplayName()).isEqualTo("Angel");
        assertThat(saved.getPasswordHash()).isEqualTo("new-hash");
        assertThat(saved.isEmailVerified()).isFalse();
    }

    @Test
    void emailIsRegistered_onlyCountsVerifiedAccounts() {
        when(userRepository.existsByEmailIgnoreCaseAndEmailVerifiedTrue("done@example.com")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCaseAndEmailVerifiedTrue("pending@example.com")).thenReturn(false);

        assertThat(accountService.emailIsRegistered("done@example.com")).isTrue();
        assertThat(accountService.emailIsRegistered("pending@example.com")).isFalse();
    }

    @Test
    void deleteAccount_rejectsAnIncorrectPasswordWithoutRemovingData() {
        AppUser user = user(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.deleteAccount("angel@example.com", "wrong-password"))
                .isInstanceOf(InvalidAccountPasswordException.class)
                .hasMessage("The password you entered is incorrect.");

        verify(userRepository, never()).deleteAccountById(any());
        verifyNoInteractions(expenseRepository, reminderLogRepository, shiftRepository,
                pushSubscriptionRepository, persistentTokenRepository, taxPaymentRepository, payoutDepositRepository,
                standingEntryRepository, taxReminderLogRepository);
    }

    @Test
    void deleteAccount_removesAllOwnedDataAndTheUser() {
        AppUser user = user(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

        accountService.deleteAccount("angel@example.com", "correct-password");

        InOrder deletionOrder = inOrder(expenseRepository, taxPaymentRepository, payoutDepositRepository,
                standingEntryRepository, taxReminderLogRepository, reminderLogRepository, shiftRepository, pushSubscriptionRepository, persistentTokenRepository, passwordResetTokenRepository, emailCodeRepository, recoveryCodeRepository, userRepository);
        deletionOrder.verify(expenseRepository).deleteAllByOwnerIdIncludingTrash(42L);
        deletionOrder.verify(taxPaymentRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(payoutDepositRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(standingEntryRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(taxReminderLogRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(reminderLogRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(shiftRepository).deleteAllByOwnerIdIncludingTrash(42L);
        deletionOrder.verify(pushSubscriptionRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(persistentTokenRepository).removeUserTokens("angel@example.com");
        deletionOrder.verify(passwordResetTokenRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(emailCodeRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(recoveryCodeRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(userRepository).deleteAccountById(42L);
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser("Angel", "angel@example.com", "stored-hash");
        user.setId(id);
        return user;
    }
}