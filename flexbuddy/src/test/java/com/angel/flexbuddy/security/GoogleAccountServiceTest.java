package com.angel.flexbuddy.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class GoogleAccountServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private AttemptLimiter limiter;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private GoogleAccountService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAccountService(userRepository, encoder, limiter);
        lenient().when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static OidcUser google(String subject, String email, boolean verified, String givenName, String fullName) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("email_verified", verified);
        if (givenName != null) claims.put("given_name", givenName);
        if (fullName != null) claims.put("name", fullName);
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new DefaultOidcUser(java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")), token);
    }

    @Test
    void aNewAddressCreatesAVerifiedAccountWithoutAPassword() {
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.empty());

        OidcUser principal = service.link(google("sub-1", "Pat@Example.com", true, "Pat", "Pat Driver"), "203.0.113.5");

        org.mockito.ArgumentCaptor<AppUser> saved = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(saved.capture());
        AppUser user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("pat@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Pat");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isPasswordSet()).isFalse();
        assertThat(user.getGoogleSubject()).isEqualTo("sub-1");
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(principal.getName()).isEqualTo("pat@example.com");
        verify(limiter).record(AttemptLimiter.REGISTER_IP, "203.0.113.5");
    }

    @Test
    void theNameFallsBackToTheFullNameThenTheAddress() {
        when(userRepository.findByGoogleSubject(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        service.link(google("sub-2", "full@example.com", true, null, "Full Name"), null);
        service.link(google("sub-3", "local@example.com", true, null, null), null);

        org.mockito.ArgumentCaptor<AppUser> saved = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(AppUser::getDisplayName).containsExactly("Full Name", "local");
    }

    @Test
    void aVerifiedAccountWithTheSameAddressIsLinkedAndKeepsItsPassword() {
        AppUser existing = new AppUser("Pat", "pat@example.com", "existing-hash");
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(existing));

        OidcUser principal = service.link(google("sub-1", "pat@example.com", true, "Pat", null), "203.0.113.5");

        assertThat(existing.getGoogleSubject()).isEqualTo("sub-1");
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
        assertThat(existing.isPasswordSet()).isTrue();
        assertThat(principal.getName()).isEqualTo("pat@example.com");
        verify(limiter, never()).record(any(), any());
    }

    @Test
    void anUnverifiedSignUpWithTheSameAddressIsTakenOverAndItsPasswordStopsWorking() {
        AppUser squatter = new AppUser("Squatter", "pat@example.com", encoder.encode("squatters-password"));
        squatter.setEmailVerified(false);
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(squatter));

        service.link(google("sub-1", "pat@example.com", true, "Pat", null), "203.0.113.5");

        assertThat(squatter.isEmailVerified()).isTrue();
        assertThat(squatter.isPasswordSet()).isFalse();
        assertThat(encoder.matches("squatters-password", squatter.getPasswordHash())).isFalse();
        assertThat(squatter.getGoogleSubject()).isEqualTo("sub-1");
    }

    @Test
    void anAddressGoogleHasNotVerifiedIsRefused() {
        assertThatThrownBy(() -> service.link(google("sub-1", "pat@example.com", false, "Pat", null), "203.0.113.5"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo("unverified_email");
        verify(userRepository, never()).save(any());
    }

    @Test
    void anAccountAlreadyLinkedToAnotherGoogleIdIsRefused() {
        AppUser existing = new AppUser("Pat", "pat@example.com", "hash");
        existing.setGoogleSubject("someone-else");
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.link(google("sub-1", "pat@example.com", true, "Pat", null), "203.0.113.5"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo("linked_elsewhere");
        assertThat(existing.getGoogleSubject()).isEqualTo("someone-else");
    }

    @Test
    void anAccountFoundByGoogleIdSignsInWithoutChangingItsOwnAddress() {
        AppUser existing = new AppUser("Pat", "old@example.com", "hash");
        existing.setGoogleSubject("sub-1");
        when(userRepository.findByGoogleSubject("sub-1")).thenReturn(Optional.of(existing));

        OidcUser principal = service.link(google("sub-1", "new@example.com", true, "Pat", null), "203.0.113.5");

        assertThat(existing.getEmail()).isEqualTo("old@example.com");
        assertThat(principal.getName()).isEqualTo("old@example.com");
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void aLockedSignUpConnectionStopsNewAccountsButNotLinking() {
        when(limiter.isLocked(AttemptLimiter.REGISTER_IP, "203.0.113.5")).thenReturn(true);
        when(userRepository.findByGoogleSubject(any())).thenReturn(Optional.empty());
        AppUser existing = new AppUser("Pat", "pat@example.com", "hash");
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.findByEmailIgnoreCase("fresh@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(google("sub-9", "fresh@example.com", true, "Fresh", null), "203.0.113.5"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo("too_many_signups");

        service.link(google("sub-1", "pat@example.com", true, "Pat", null), "203.0.113.5");
        assertThat(existing.getGoogleSubject()).isEqualTo("sub-1");
    }
}
