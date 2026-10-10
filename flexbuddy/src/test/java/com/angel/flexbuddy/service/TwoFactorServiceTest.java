package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.exception.InvalidAccountPasswordException;
import com.angel.flexbuddy.exception.InvalidTwoFactorCodeException;
import com.angel.flexbuddy.mail.EmailCodeMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.EmailCodePurpose;
import com.angel.flexbuddy.model.TimestampListener;
import com.angel.flexbuddy.model.TwoFactorMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.RecoveryCodeRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.security.SecurityLimitsProperties;
import com.angel.flexbuddy.security.Totp;
import com.angel.flexbuddy.security.TwoFactorSecretCipher;

@DataJpaTest(properties = {"flexbuddy.security.remember-me-key=two-factor-test-key",
        "flexbuddy.security.two-factor-key=two-factor-test-cipher-key"})
@ActiveProfiles("test")
@Import({TwoFactorService.class, EmailCodeService.class, TwoFactorSecretCipher.class, AttemptLimiter.class,
        JpaAuditingConfig.class, TimestampListener.class, TwoFactorServiceTest.Beans.class})
class TwoFactorServiceTest {

    private static final Instant START = Instant.parse("2026-10-10T12:00:00Z");
    private static final String PASSWORD = "right-password-1";

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
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }

    @Autowired
    private TwoFactorService service;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RecoveryCodeRepository recoveryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
        clock.advance(Duration.between(clock.instant(), START));
        angel = userRepository.save(new AppUser("Angel", "angel@example.com", passwordEncoder.encode(PASSWORD)));
    }

    private long step() {
        return clock.instant().getEpochSecond() / 30;
    }

    /** Turns the app method on with a fresh secret and returns that secret's bytes. */
    private byte[] enableApp(List<String> recovery) {
        TwoFactorService.AppSetup setup = service.beginApp(angel);
        byte[] key = Totp.fromBase32(setup.secret());
        recovery.addAll(service.confirmApp(angel, setup.secret(), Totp.code(key, step())));
        return key;
    }

    @Test
    void confirmingTheAppStoresAnEncryptedSecretAndGivesTenFormattedRecoveryCodes() {
        TwoFactorService.AppSetup setup = service.beginApp(angel);
        byte[] key = Totp.fromBase32(setup.secret());

        List<String> codes = service.confirmApp(angel, setup.secret(), Totp.code(key, step()));

        assertThat(angel.getTwoFactorMethod()).isEqualTo(TwoFactorMethod.APP);
        assertThat(angel.getTotpSecret()).isNotBlank().doesNotContain(setup.secret());
        assertThat(codes).hasSize(10).allMatch(code -> code.matches("[A-HJKMNP-Z2-9]{5}-[A-HJKMNP-Z2-9]{5}"));
        assertThat(service.recoveryCodesLeft(angel)).isEqualTo(10);
        assertThat(setup.qrSvg()).startsWith("<svg").contains("<path");
        assertThat(setup.groupedSecret()).matches("([A-Z2-7]{4} ){7}[A-Z2-7]{4}");
    }

    @Test
    void aWrongCodeDoesNotTurnTheAppOn() {
        TwoFactorService.AppSetup setup = service.beginApp(angel);
        byte[] key = Totp.fromBase32(setup.secret());
        String wrong = Totp.code(key, step() + 5);

        assertThatThrownBy(() -> service.confirmApp(angel, setup.secret(), wrong))
                .isInstanceOf(InvalidTwoFactorCodeException.class);

        assertThat(angel.getTwoFactorMethod()).isNull();
        assertThat(angel.getTotpSecret()).isNull();
    }

    @Test
    void anAppCodeWorksOnceAndTheNextStepsCodeWorksToo() {
        byte[] key = enableApp(new ArrayList<>());
        clock.advance(Duration.ofSeconds(31));

        String current = Totp.code(key, step());
        assertThat(service.verifySignIn(angel, current)).isEqualTo(TwoFactorService.Result.OK);
        // The same code a second time is a replay.
        assertThat(service.verifySignIn(angel, current)).isEqualTo(TwoFactorService.Result.WRONG);
        // The next step's code, which a phone with a fast clock would show, is still accepted once.
        assertThat(service.verifySignIn(angel, Totp.code(key, step() + 1))).isEqualTo(TwoFactorService.Result.OK);
    }

    @Test
    void aRecoveryCodeWorksOnceAndIsReadCaseAndDashInsensitively() {
        List<String> recovery = new ArrayList<>();
        enableApp(recovery);
        String code = recovery.get(0);

        assertThat(service.verifySignIn(angel, code.toLowerCase().replace("-", " "))).isEqualTo(TwoFactorService.Result.OK_RECOVERY);
        assertThat(service.verifySignIn(angel, code)).isEqualTo(TwoFactorService.Result.WRONG);
        assertThat(service.recoveryCodesLeft(angel)).isEqualTo(9);
    }

    @Test
    void fiveWrongCodesLockTheEmailsSignIn() {
        enableApp(new ArrayList<>());

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(service.verifySignIn(angel, "000000")).isEqualTo(TwoFactorService.Result.WRONG);
        }

        assertThat(limiter.isLocked(AttemptLimiter.LOGIN_EMAIL, "angel@example.com")).isTrue();
    }

    @Test
    void turningOffNeedsTheRightPasswordAndACode() {
        List<String> recovery = new ArrayList<>();
        byte[] key = enableApp(recovery);
        clock.advance(Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.disable(angel, "wrong-password", Totp.code(key, step())))
                .isInstanceOf(InvalidAccountPasswordException.class);
        assertThat(angel.getTwoFactorMethod()).isEqualTo(TwoFactorMethod.APP);

        assertThatThrownBy(() -> service.disable(angel, PASSWORD, "000000"))
                .isInstanceOf(InvalidTwoFactorCodeException.class);
        assertThat(angel.getTwoFactorMethod()).isEqualTo(TwoFactorMethod.APP);

        service.disable(angel, PASSWORD, Totp.code(key, step()));

        assertThat(angel.getTwoFactorMethod()).isNull();
        assertThat(angel.getTotpSecret()).isNull();
        assertThat(angel.getTotpLastStep()).isNull();
        assertThat(recoveryRepository.countByOwnerIdAndUsedAtIsNull(angel.getId())).isZero();
    }

    @Test
    void anAccountWithoutAPasswordTurnsTwoStepOffWithACodeAlone() {
        List<String> recovery = new ArrayList<>();
        byte[] key = enableApp(recovery);
        angel.setPasswordSet(false);
        clock.advance(Duration.ofSeconds(60));

        service.disable(angel, null, Totp.code(key, step()));

        assertThat(angel.getTwoFactorMethod()).isNull();
    }

    @Test
    void newRecoveryCodesNeedACodeAndReplaceTheOldOnes() {
        List<String> recovery = new ArrayList<>();
        byte[] key = enableApp(recovery);
        clock.advance(Duration.ofSeconds(60));

        assertThatThrownBy(() -> service.regenerateRecoveryCodes(angel, "000000"))
                .isInstanceOf(InvalidTwoFactorCodeException.class);

        List<String> fresh = service.regenerateRecoveryCodes(angel, Totp.code(key, step()));

        assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(recovery);
        assertThat(service.verifySignIn(angel, recovery.get(0))).isEqualTo(TwoFactorService.Result.WRONG);
        assertThat(service.verifySignIn(angel, fresh.get(0))).isEqualTo(TwoFactorService.Result.OK_RECOVERY);
    }

    @Test
    void theEmailMethodSendsASignInCodeAndConfirmingItTurnsEmailOn() {
        assertThat(service.beginEmail(angel, "203.0.113.4")).isEqualTo(EmailCodeService.SendResult.SENT);
        String code = mailer.codes.get(0);

        List<String> recovery = service.confirmEmail(angel, code);

        assertThat(angel.getTwoFactorMethod()).isEqualTo(TwoFactorMethod.EMAIL);
        assertThat(angel.getTotpSecret()).isNull();
        assertThat(recovery).hasSize(10);

        service.sendSignInEmail(angel, "203.0.113.4");
        assertThat(service.verifySignIn(angel, mailer.codes.get(1))).isEqualTo(TwoFactorService.Result.OK);
    }

    @Test
    void aWrongEmailCodeDoesNotTurnEmailOn() {
        service.beginEmail(angel, "203.0.113.4");
        String code = mailer.codes.get(0);
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.confirmEmail(angel, wrong)).isInstanceOf(InvalidTwoFactorCodeException.class);

        assertThat(angel.getTwoFactorMethod()).isNull();
    }
}
