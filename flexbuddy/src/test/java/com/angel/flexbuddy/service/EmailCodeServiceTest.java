package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.mail.EmailCodeMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.EmailCode;
import com.angel.flexbuddy.model.EmailCodePurpose;
import com.angel.flexbuddy.model.TimestampListener;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.EmailCodeRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.security.SecurityLimitsProperties;

@DataJpaTest(properties = "flexbuddy.security.remember-me-key=code-test-key")
@ActiveProfiles("test")
@Import({EmailCodeService.class, AttemptLimiter.class, JpaAuditingConfig.class, TimestampListener.class,
        EmailCodeServiceTest.Beans.class})
class EmailCodeServiceTest {

    private static final Instant START = Instant.parse("2026-10-01T12:00:00Z");

    /** Keeps every code instead of emailing it. */
    static class RecordingMailer implements EmailCodeMailer {
        final List<String> codes = new ArrayList<>();

        @Override
        public void sendCode(String toEmail, String displayName, String code, EmailCodePurpose purpose) {
            codes.add(code);
        }
    }

    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }

        @Bean
        SecurityLimitsProperties limits() {
            return new SecurityLimitsProperties(
                    new SecurityLimitsProperties.Login(5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                    new SecurityLimitsProperties.Registration(10, Duration.ofHours(1)),
                    new SecurityLimitsProperties.Reset(3, 10, Duration.ofHours(1), Duration.ofMinutes(30)),
                    new SecurityLimitsProperties.Codes(Duration.ofMinutes(15), 5, 5, 20, Duration.ofHours(1)));
        }

        @Bean
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }

    @Autowired
    private EmailCodeService service;

    @Autowired
    private EmailCodeRepository codeRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RecordingMailer mailer;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AttemptLimiter limiter;

    private AppUser angel;

    @BeforeEach
    void setUp() {
        mailer.codes.clear();
        limiter.clearAll();
        // The context is shared, so an earlier test may have moved the clock.
        clock.advance(Duration.between(clock.instant(), START));
        angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
    }

    private String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private EmailCode latest() {
        return codeRepository.findAll().stream().max((a, b) -> a.getId().compareTo(b.getId())).orElseThrow();
    }

    @Test
    void sendingStoresAKeyedHashAndEmailsASixDigitCode() throws Exception {
        assertThat(service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.4"))
                .isEqualTo(EmailCodeService.SendResult.SENT);

        String code = mailer.codes.get(0);
        assertThat(code).matches("\\d{6}");
        EmailCode stored = latest();
        assertThat(stored.getCodeHash()).hasSize(64).doesNotContain(code);
        // Not the plain hash of the code, which a leaked database could be searched for.
        assertThat(stored.getCodeHash()).isNotEqualTo(sha256(code))
                .isNotEqualTo(sha256(angel.getId() + ":VERIFY_EMAIL:" + code));
        assertThat(stored.getExpiresAt()).isEqualTo(START.plus(Duration.ofMinutes(15)));
    }

    @Test
    void aRightCodeWorksOnceAndThenIsUsedUp() {
        service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.4");
        String code = mailer.codes.get(0);

        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, code)).isEqualTo(EmailCodeService.CheckResult.OK);
        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, code)).isEqualTo(EmailCodeService.CheckResult.EXPIRED);
    }

    @Test
    void sendingAnotherCancelsTheEarlierCode() {
        service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.4");
        service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.4");
        String first = mailer.codes.get(0);
        String second = mailer.codes.get(1);
        assumeThat(first).isNotEqualTo(second);

        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, first)).isEqualTo(EmailCodeService.CheckResult.WRONG);
        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, second)).isEqualTo(EmailCodeService.CheckResult.OK);
    }

    @Test
    void fiveWrongTriesLockTheCodeEvenForTheRightOne() {
        service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.4");
        String code = mailer.codes.get(0);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, wrong)).isEqualTo(EmailCodeService.CheckResult.WRONG);
        }
        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, wrong)).isEqualTo(EmailCodeService.CheckResult.TOO_MANY);
        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, code)).isEqualTo(EmailCodeService.CheckResult.TOO_MANY);
    }

    @Test
    void aCodeExpiresAfterFifteenMinutes() {
        service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.4");
        String code = mailer.codes.get(0);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, code)).isEqualTo(EmailCodeService.CheckResult.EXPIRED);
    }

    @Test
    void spacesAreIgnoredAndAnythingNotSixDigitsIsWrongWithoutCounting() {
        service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.4");
        String code = mailer.codes.get(0);

        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, "12345")).isEqualTo(EmailCodeService.CheckResult.WRONG);
        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, "abcdef")).isEqualTo(EmailCodeService.CheckResult.WRONG);
        assertThat(latest().getAttempts()).isZero();

        String spaced = " " + code.substring(0, 3) + " " + code.substring(3) + " ";
        assertThat(service.check(angel, EmailCodePurpose.VERIFY_EMAIL, spaced)).isEqualTo(EmailCodeService.CheckResult.OK);
    }

    @Test
    void theSixthSendInAnHourForOneAddressIsLimitedAndSendsNothing() {
        for (int send = 1; send <= 5; send++) {
            assertThat(service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113." + send))
                    .isEqualTo(EmailCodeService.SendResult.SENT);
        }

        assertThat(service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "203.0.113.99"))
                .isEqualTo(EmailCodeService.SendResult.LIMITED);
        assertThat(mailer.codes).hasSize(5);
    }

    @Test
    void aBusyConnectionIsLimitedAcrossAddresses() {
        for (int send = 1; send <= 20; send++) {
            AppUser other = userRepository.save(new AppUser("Driver " + send, "driver" + send + "@example.com", "hash"));
            assertThat(service.send(other, EmailCodePurpose.VERIFY_EMAIL, "198.51.100.8"))
                    .isEqualTo(EmailCodeService.SendResult.SENT);
        }

        assertThat(service.send(angel, EmailCodePurpose.VERIFY_EMAIL, "198.51.100.8"))
                .isEqualTo(EmailCodeService.SendResult.LIMITED);
    }
}
