package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.angel.flexbuddy.exception.InvalidResetTokenException;
import com.angel.flexbuddy.mail.PasswordResetMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PasswordResetToken;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PasswordResetTokenRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.security.SecurityLimitsProperties;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final String LINK_PREFIX = "https://flexbuddy.onrender.com/reset-password?token=";

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersistentTokenRepository persistentTokenRepository;

    @Mock
    private AttemptLimiter limiter;

    @Mock
    private PasswordResetMailer mailer;

    private MutableClock clock;
    private PasswordResetService service;
    private AppUser angel;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        SecurityLimitsProperties limits = new SecurityLimitsProperties(
                new SecurityLimitsProperties.Login(5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new SecurityLimitsProperties.Registration(10, Duration.ofHours(1)),
                new SecurityLimitsProperties.Reset(3, 10, Duration.ofHours(1), Duration.ofMinutes(30)));
        service = new PasswordResetService(tokenRepository, userRepository, passwordEncoder, persistentTokenRepository,
                limiter, mailer, clock, limits, "https://flexbuddy.onrender.com");
        angel = new AppUser("Angel", "angel@example.com", "old-hash");
        angel.setId(42L);
    }

    private String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    /** Asks for a reset and returns the token from the emailed link, along with the stored row. */
    private String requestAndCaptureToken(ArgumentCaptor<PasswordResetToken> saved) {
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(angel));
        service.requestReset("  Angel@Example.com ", "203.0.113.9");
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq("angel@example.com"), eq("Angel"), link.capture());
        verify(tokenRepository).save(saved.capture());
        assertThat(link.getValue()).startsWith(LINK_PREFIX);
        return link.getValue().substring(LINK_PREFIX.length());
    }

    @Test
    void aResetStoresOnlyTheHashAndEmailsTheMatchingLink() throws Exception {
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);

        String token = requestAndCaptureToken(saved);

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256(token)).hasSize(64);
        assertThat(saved.getValue().getTokenHash()).doesNotContain(token);
        assertThat(saved.getValue().getOwner()).isSameAs(angel);
        assertThat(saved.getValue().getUsedAt()).isNull();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void aLinkExpiresAfter30Minutes() throws Exception {
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        String token = requestAndCaptureToken(saved);
        when(tokenRepository.findByTokenHash(sha256(token))).thenReturn(Optional.of(saved.getValue()));

        clock.advance(Duration.ofMinutes(29).plusSeconds(59));
        assertThat(service.checkToken(token)).isPresent();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.checkToken(token)).isEmpty();
    }

    @Test
    void askingAgainRetiresEarlierLinks() {
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(angel));

        service.requestReset("angel@example.com", "203.0.113.9");

        InOrder order = inOrder(tokenRepository);
        order.verify(tokenRepository).retireUnused(42L, NOW);
        order.verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void anUnknownEmailSendsNothingButStillCountsTowardTheLimit() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com", "203.0.113.9");

        verifyNoInteractions(mailer);
        verify(tokenRepository, never()).save(any());
        verify(limiter).record(AttemptLimiter.RESET_EMAIL, "nobody@example.com");
        verify(limiter).record(AttemptLimiter.RESET_IP, "203.0.113.9");
    }

    @Test
    void overTheLimitNothingIsSent() {
        lenient().when(limiter.isLocked(AttemptLimiter.RESET_EMAIL, "angel@example.com")).thenReturn(true);

        service.requestReset("angel@example.com", "203.0.113.9");

        verifyNoInteractions(mailer);
        verify(tokenRepository, never()).save(any());
        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(limiter, never()).record(any(), any());
    }

    @Test
    void aBusyConnectionIsLimitedToo() {
        lenient().when(limiter.isLocked(AttemptLimiter.RESET_IP, "203.0.113.9")).thenReturn(true);

        service.requestReset("angel@example.com", "203.0.113.9");

        verifyNoInteractions(mailer);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resettingSetsTheNewHashUsesUpTheLinkSignsOutRememberedDevicesAndClearsTheLock() throws Exception {
        PasswordResetToken link = linkFor("token-value", NOW.plusSeconds(600), null);
        when(passwordEncoder.encode("new-password-1")).thenReturn("new-hash");

        service.resetPassword("token-value", "new-password-1");

        assertThat(angel.getPasswordHash()).isEqualTo("new-hash");
        assertThat(link.getUsedAt()).isEqualTo(NOW);
        verify(tokenRepository).retireUnused(42L, NOW);
        verify(persistentTokenRepository).removeUserTokens("angel@example.com");
        verify(limiter).clear(AttemptLimiter.LOGIN_EMAIL, "angel@example.com");
    }

    @Test
    void aUsedOrExpiredLinkCannotResetAndChangesNothing() throws Exception {
        linkFor("used-token", NOW.plusSeconds(600), NOW.minusSeconds(10));
        linkFor("old-token", NOW.minusSeconds(1), null);
        when(tokenRepository.findByTokenHash(sha256("unknown-token"))).thenReturn(Optional.empty());

        for (String token : new String[] {"used-token", "old-token", "unknown-token", "", null}) {
            assertThatThrownBy(() -> service.resetPassword(token, "new-password-1"))
                    .isInstanceOf(InvalidResetTokenException.class);
        }

        assertThat(angel.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(persistentTokenRepository, never()).removeUserTokens(any());
        verify(tokenRepository, never()).retireUnused(anyLong(), any());
    }

    @Test
    void purgeDeletesLinksExpiredMoreThanADay() {
        service.purgeExpired();

        verify(tokenRepository).deleteExpiredBefore(NOW.minus(Duration.ofDays(1)));
    }

    private PasswordResetToken linkFor(String token, Instant expiresAt, Instant usedAt) throws Exception {
        PasswordResetToken link = new PasswordResetToken();
        link.setOwner(angel);
        link.setTokenHash(sha256(token));
        link.setCreatedAt(NOW.minusSeconds(60));
        link.setExpiresAt(expiresAt);
        link.setUsedAt(usedAt);
        when(tokenRepository.findByTokenHash(sha256(token))).thenReturn(Optional.of(link));
        return link;
    }
}
