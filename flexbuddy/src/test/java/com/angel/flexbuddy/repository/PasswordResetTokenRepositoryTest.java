package com.angel.flexbuddy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PasswordResetToken;
import com.angel.flexbuddy.model.TimestampListener;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, TimestampListener.class})
class PasswordResetTokenRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    private AppUser angel;
    private AppUser sam;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        angel = users.save(new AppUser("Angel", "angel@example.com", "hash"));
        sam = users.save(new AppUser("Sam", "sam@example.com", "hash"));
    }

    private PasswordResetToken token(AppUser owner, String hash, Instant expiresAt, Instant usedAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setOwner(owner);
        token.setTokenHash(hash.repeat(64).substring(0, 64));
        token.setCreatedAt(NOW);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return tokens.saveAndFlush(token);
    }

    @Test
    void findByTokenHashFindsOnlyThatLink() {
        token(angel, "a", NOW.plusSeconds(1800), null);

        assertThat(tokens.findByTokenHash("a".repeat(64))).isPresent();
        assertThat(tokens.findByTokenHash("b".repeat(64))).isEmpty();
    }

    @Test
    void retireUnusedCancelsOnlyThatOwnersUnusedLinks() {
        PasswordResetToken mine = token(angel, "a", NOW.plusSeconds(1800), null);
        PasswordResetToken alreadyUsed = token(angel, "b", NOW.plusSeconds(1800), NOW.minusSeconds(60));
        PasswordResetToken theirs = token(sam, "c", NOW.plusSeconds(1800), null);

        int changed = tokens.retireUnused(angel.getId(), NOW);
        entityManager.clear();

        assertThat(changed).isOne();
        assertThat(tokens.findById(mine.getId()).orElseThrow().getUsedAt()).isEqualTo(NOW);
        assertThat(tokens.findById(alreadyUsed.getId()).orElseThrow().getUsedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(tokens.findById(theirs.getId()).orElseThrow().getUsedAt()).isNull();
    }

    @Test
    void deleteExpiredBeforeRemovesOnlyLinksThatExpiredBeforeTheCutoff() {
        token(angel, "a", NOW.minusSeconds(100_000), null);
        token(angel, "b", NOW.minusSeconds(60), null);
        token(sam, "c", NOW.plusSeconds(1800), null);

        int removed = tokens.deleteExpiredBefore(NOW.minusSeconds(86_400));

        assertThat(removed).isOne();
        assertThat(tokens.count()).isEqualTo(2);
    }

    @Test
    void deleteAllByOwnerIdRemovesOnlyThatOwnersLinks() {
        token(angel, "a", NOW.plusSeconds(1800), null);
        token(angel, "b", NOW.plusSeconds(1800), null);
        token(sam, "c", NOW.plusSeconds(1800), null);

        int removed = tokens.deleteAllByOwnerId(angel.getId());

        assertThat(removed).isEqualTo(2);
        assertThat(tokens.count()).isOne();
    }

    @Test
    void aTokenHashIsUnique() {
        token(angel, "a", NOW.plusSeconds(1800), null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> token(sam, "a", NOW.plusSeconds(1800), null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
