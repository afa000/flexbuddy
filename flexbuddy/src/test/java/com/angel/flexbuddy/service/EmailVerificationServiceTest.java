package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.TimestampListener;
import com.angel.flexbuddy.repository.AppUserRepository;

@DataJpaTest
@ActiveProfiles("test")
@Import({EmailVerificationService.class, AccountService.class, JpaAuditingConfig.class, TimestampListener.class})
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Autowired
    private EmailVerificationService verification;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailCodeService codes;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private PersistentTokenRepository persistentTokenRepository;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private AppUser account(String email, boolean verified, Duration age) {
        AppUser user = new AppUser("Driver", email, "hash");
        user.setEmailVerified(verified);
        user = userRepository.save(user);
        jdbcTemplate.update("update app_users set created_at = ? where id = ?",
                Timestamp.from(NOW.minus(age)), user.getId());
        return user;
    }

    @Test
    void purgeAbandonedDeletesOnlyUnverifiedSignUpsOlderThanADay() {
        account("old-unverified@example.com", false, Duration.ofHours(25));
        account("fresh-unverified@example.com", false, Duration.ofHours(23));
        account("old-verified@example.com", true, Duration.ofHours(25));

        verification.purgeAbandoned();

        assertThat(userRepository.findAll()).extracting(AppUser::getEmail)
                .containsExactlyInAnyOrder("fresh-unverified@example.com", "old-verified@example.com");
    }
}
