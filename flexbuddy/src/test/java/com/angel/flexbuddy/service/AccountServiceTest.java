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
    void deleteAccount_rejectsAnIncorrectPasswordWithoutRemovingData() {
        AppUser user = user(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.deleteAccount("angel@example.com", "wrong-password"))
                .isInstanceOf(InvalidAccountPasswordException.class)
                .hasMessage("The password you entered is incorrect.");

        verify(userRepository, never()).deleteAccountById(any());
        verifyNoInteractions(expenseRepository, reminderLogRepository, shiftRepository,
                pushSubscriptionRepository, persistentTokenRepository);
    }

    @Test
    void deleteAccount_removesAllOwnedDataAndTheUser() {
        AppUser user = user(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

        accountService.deleteAccount("angel@example.com", "correct-password");

        InOrder deletionOrder = inOrder(expenseRepository, reminderLogRepository, shiftRepository,
                pushSubscriptionRepository, persistentTokenRepository, userRepository);
        deletionOrder.verify(expenseRepository).deleteAllByOwnerIdIncludingTrash(42L);
        deletionOrder.verify(reminderLogRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(shiftRepository).deleteAllByOwnerIdIncludingTrash(42L);
        deletionOrder.verify(pushSubscriptionRepository).deleteAllByOwnerId(42L);
        deletionOrder.verify(persistentTokenRepository).removeUserTokens("angel@example.com");
        deletionOrder.verify(userRepository).deleteAccountById(42L);
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser("Angel", "angel@example.com", "stored-hash");
        user.setId(id);
        return user;
    }
}