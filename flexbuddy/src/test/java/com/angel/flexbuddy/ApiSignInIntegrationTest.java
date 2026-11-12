package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import com.angel.flexbuddy.api.AppTokenService;
import com.angel.flexbuddy.mail.EmailCodeMailer;
import com.angel.flexbuddy.model.*;
import com.angel.flexbuddy.repository.*;
import com.angel.flexbuddy.security.*;
import com.angel.flexbuddy.service.*;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.*;

@SpringBootTest(properties = {"flexbuddy.api.enabled=true", "flexbuddy.security.remember-me-key=api-test-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSignInIntegrationTest {
    private static final String EMAIL = "native@example.com";
    private static final String PASSWORD = "native-test-password";
    static class Mailer implements EmailCodeMailer {
        final List<String> codes = new ArrayList<>();
        @Override public void sendCode(String email, String name, String code, EmailCodePurpose purpose) {
            codes.add(code);
        }
    }
    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock apiClock() { return new MutableClock(Instant.parse("2026-11-01T12:00:00Z")); }
        @Bean @Primary Mailer apiMailer() { return new Mailer(); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired AppTokenRepository tokens;
    @Autowired AppSignInChallengeRepository challenges;
    @Autowired EmailCodeRepository emails;
    @Autowired RecoveryCodeRepository recovery;
    @Autowired PasswordResetTokenRepository resets;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired AttemptLimiter limiter;
    @Autowired Mailer mailer;
    @Autowired TwoFactorService twoFactor;
    @Autowired PasswordResetService passwordReset;
    @Autowired AccountService accounts;
    @Autowired AppTokenService tokenService;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired EmailVerificationService verification;

    @BeforeEach void before() {
        wipe();
        limiter.clearAll();
        mailer.codes.clear();
        jdbc.execute("create table if not exists persistent_logins (username varchar(254) not null, "
                + "series varchar(64) primary key, token varchar(64) not null, last_used timestamp not null)");
        jdbc.update("delete from persistent_logins");
    }
    @AfterEach void wipe() {
        tokens.deleteAll();
        challenges.deleteAll();
        emails.deleteAll();
        recovery.deleteAll();
        resets.deleteAll();
        users.deleteAll();
    }
    private AppUser user() {
        return users.save(new AppUser("Native Driver", EMAIL, encoder.encode(PASSWORD)));
    }
    private MvcResult postJson(String path, Object body, int expected) throws Exception {
        return mvc.perform(post("/api/v1" + path).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andExpect(header().doesNotExist("Location")).andReturn();
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private JsonNode signIn(String email, String password, int expected) throws Exception {
        return body(postJson("/auth/sign-in", Map.of("email", email, "password", password, "deviceName", "Test phone"), expected));
    }
    private JsonNode signIn() throws Exception { return signIn(EMAIL, PASSWORD, 200); }
    private JsonNode code(String challenge, String code, int expected) throws Exception {
        return body(postJson("/auth/code", Map.of("challenge", challenge, "code", code), expected));
    }
    private String latestCode() { return mailer.codes.getLast(); }
    private void account(String token, int expected) throws Exception {
        mvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected)).andExpect(header().doesNotExist("Location"));
    }
    private byte[] enableApp(AppUser user, List<String> recoveryCodes) {
        var setup = twoFactor.beginApp(user);
        byte[] key = Totp.fromBase32(setup.secret());
        recoveryCodes.addAll(twoFactor.confirmApp(user, setup.secret(), Totp.code(key, clock.instant().getEpochSecond() / 30)));
        clock.advance(Duration.ofSeconds(35));
        return key;
    }

    @Test void tokenIsHashedAccountIsScopedAndSignOutRevokesOnlyThisDevice() throws Exception {
        user();
        var result = signIn();
        String first = result.get("token").asText();
        assertThat(first).matches("fbt_[A-Za-z0-9_-]{43}");
        assertThat(tokens.findAll().getFirst().getTokenHash()).matches("[0-9a-f]{64}").isNotEqualTo(first);
        String second = signIn().get("token").asText();
        users.save(new AppUser("Other Driver", "other@example.com", encoder.encode(PASSWORD)));
        mvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("Native Driver"));
        mvc.perform(post("/api/v1/auth/sign-out").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent());
        account(first, 401);
        account(second, 200);
        assertThat(result.get("account").get("email").asText()).isEqualTo(EMAIL);
    }

    @Test void noTokenInvalidTokenAndBrowserCookiesCannotAuthenticateApi() throws Exception {
        user();
        var login = mvc.perform(post("/login").with(csrf()).secure(true)
                .param("username", EMAIL).param("password", PASSWORD).param("remember-me", "on"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/v1/account").session(session)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
        Cookie cookie = login.getResponse().getCookie("FLEXBUDDY_REMEMBER_ME");
        assertThat(cookie).isNotNull();
        mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account")).andExpect(status().isUnauthorized());
        account("unknown", 401);
        account("fbt_" + "a".repeat(43), 401);
        mvc.perform(post("/api/v1/auth/sign-out").session(session)).andExpect(status().isUnauthorized());
        // API requests did not corrupt the existing browser session; web CSRF still applies.
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        mvc.perform(post("/account/language").session(session).param("language", "es")).andExpect(status().isForbidden());
    }

    @Test void signInCreatesNeitherSessionNorCookies() throws Exception {
        user();
        var result = postJson("/auth/sign-in", Map.of("email", EMAIL, "password", PASSWORD), 200);
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookies()).isEmpty();
        mvc.perform(get("/").header("Authorization", "Bearer " + body(result).get("token").asText()))
                .andExpect(status().is3xxRedirection());
    }

    @Test void credentialFailuresAreUniformAndShareWebLocks() throws Exception {
        AppUser user = user();
        assertThat(signIn(EMAIL, "incorrect", 401).get("error").asText()).isEqualTo("invalid_credentials");
        assertThat(signIn("unknown@example.com", PASSWORD, 401).get("error").asText()).isEqualTo("invalid_credentials");
        user.setPasswordSet(false);
        users.save(user);
        assertThat(signIn(EMAIL, PASSWORD, 401).get("error").asText()).isEqualTo("invalid_credentials");
        user.setPasswordSet(true);
        users.save(user);
        // Three more failures: the first two previous failures for this email each counted once.
        signIn(EMAIL, "incorrect", 401);
        signIn(EMAIL, "incorrect", 401);
        signIn(EMAIL, "incorrect", 401);
        assertThat(limiter.isLocked(AttemptLimiter.LOGIN_EMAIL, EMAIL)).isTrue();
        signIn(EMAIL, PASSWORD, 429);
        mvc.perform(post("/login").with(csrf()).param("username", EMAIL).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?locked"));
    }

    @Test void ipLockRefusesCorrectCredentialsBeforeIssuingAnything() throws Exception {
        user();
        for (int i = 0; i < 20; i++) limiter.record(AttemptLimiter.LOGIN_IP, "127.0.0.1");
        org.mockito.Mockito.clearInvocations(encoder);
        assertThat(signIn(EMAIL, PASSWORD, 429).get("retryAfterMinutes").asInt()).isPositive();
        org.mockito.Mockito.verify(encoder, org.mockito.Mockito.never())
                .matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(tokens.count()).isZero();
        assertThat(challenges.count()).isZero();
    }

    @Test void emailVerificationConsumesChallengeAndCannotBeReplayed() throws Exception {
        AppUser user = user();
        user.setEmailVerified(false);
        users.save(user);
        var pending = signIn();
        assertThat(pending.get("purpose").asText()).isEqualTo("verify_email");
        String challenge = pending.get("challenge").asText();
        assertThat(challenges.findAll().getFirst().getChallengeHash()).isNotEqualTo(challenge);
        assertThat(tokens.count()).isZero();
        code(challenge, "bad", 400);
        var result = code(challenge, latestCode(), 200);
        assertThat(result.get("status").asText()).isEqualTo("signed_in");
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        code(challenge, latestCode(), 401);
        account(result.get("token").asText(), 200);
    }

    @Test void appCodeAndRecoveryCodeWorkButUsedChallengeAndTotpStepDoNot() throws Exception {
        List<String> codes = new ArrayList<>();
        byte[] key = enableApp(user(), codes);
        String pending = signIn().get("challenge").asText();
        String totp = Totp.code(key, clock.instant().getEpochSecond() / 30);
        assertThat(code(pending, totp, 200).get("status").asText()).isEqualTo("signed_in");
        code(pending, totp, 401);
        pending = signIn().get("challenge").asText();
        code(pending, totp, 400);
        assertThat(code(pending, codes.getFirst(), 200).get("recoveryCodeUsed").asBoolean()).isTrue();
        pending = signIn().get("challenge").asText();
        code(pending, codes.getFirst(), 400);
        postJson("/auth/code/resend", Map.of("challenge", pending), 400);
    }

    @Test void passwordSuccessDoesNotClearPendingSecondFactorFailures() throws Exception {
        enableApp(user(), new ArrayList<>());
        for (int i = 0; i < 4; i++) {
            String challenge = signIn().get("challenge").asText();
            code(challenge, "bad", 400);
        }
        String challenge = signIn().get("challenge").asText();
        code(challenge, "bad", 429);
        assertThat(challenges.count()).isZero();
        signIn(EMAIL, PASSWORD, 429);
    }

    @Test void emailSecondFactorUsesSharedResendLimitsAndSupersedesOldCode() throws Exception {
        AppUser user = user();
        user.setTwoFactorMethod(TwoFactorMethod.EMAIL);
        users.save(user);
        String challenge = signIn().get("challenge").asText();
        String oldCode = latestCode();
        postJson("/auth/code/resend", Map.of("challenge", challenge), 204);
        if (!oldCode.equals(latestCode())) code(challenge, oldCode, 400);
        assertThat(code(challenge, latestCode(), 200).get("status").asText()).isEqualTo("signed_in");
        challenge = signIn().get("challenge").asText();
        postJson("/auth/code/resend", Map.of("challenge", challenge), 204);
        postJson("/auth/code/resend", Map.of("challenge", challenge), 204);
        postJson("/auth/code/resend", Map.of("challenge", challenge), 429);
    }

    @Test void emailVerificationNeverBypassesAnEnabledSecondFactor() throws Exception {
        AppUser user = user();
        user.setEmailVerified(false);
        user.setTwoFactorMethod(TwoFactorMethod.EMAIL);
        users.save(user);
        String verification = signIn().get("challenge").asText();
        var second = code(verification, latestCode(), 200);
        assertThat(second.get("purpose").asText()).isEqualTo("two_factor");
        assertThat(tokens.count()).isZero();
        assertThat(code(second.get("challenge").asText(), latestCode(), 200).get("status").asText()).isEqualTo("signed_in");
    }

    @Test void expiredAndReplacedChallengesCannotFinish() throws Exception {
        AppUser user = user();
        user.setEmailVerified(false);
        users.save(user);
        String first = signIn().get("challenge").asText();
        String second = signIn().get("challenge").asText();
        code(first, latestCode(), 401);
        clock.advance(Duration.ofMinutes(10));
        code(second, latestCode(), 401);
        postJson("/auth/code/resend", Map.of("challenge", second), 401);
        code("fbc_" + "x".repeat(43), "123456", 401);
    }

    @Test void expiredEmailCodeIsReportedWhileChallengeStillExists() throws Exception {
        AppUser user = user();
        user.setTwoFactorMethod(TwoFactorMethod.EMAIL);
        users.save(user);
        String pending = signIn().get("challenge").asText();
        var stored = emails.findAll().getFirst();
        stored.setExpiresAt(clock.instant());
        emails.save(stored);
        assertThat(code(pending, latestCode(), 400).get("error").asText()).isEqualTo("code_expired");
    }

    @Test void expirySlidesOnlyHourlyAndExpiredTokensStayInvalid() throws Exception {
        user();
        String token = signIn().get("token").asText();
        AppToken initial = tokens.findAll().getFirst();
        clock.advance(Duration.ofMinutes(59));
        account(token, 200);
        assertThat(tokens.findAll().getFirst().getExpiresAt()).isEqualTo(initial.getExpiresAt());
        clock.advance(Duration.ofMinutes(1));
        account(token, 200);
        assertThat(tokens.findAll().getFirst().getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        clock.advance(Duration.ofMinutes(1));
        account(token, 200);
        assertThat(tokens.findAll().getFirst().getLastUsedAt()).isEqualTo(initial.getLastUsedAt().plus(Duration.ofHours(1)));
        clock.advance(Duration.ofDays(30));
        account(token, 401);
    }

    @Test void passwordResetRevokesEveryTokenAndChallenge() throws Exception {
        AppUser user = user();
        String first = signIn().get("token").asText();
        signIn();
        user.setTwoFactorMethod(TwoFactorMethod.EMAIL);
        users.save(user);
        String pending = signIn().get("challenge").asText();
        PasswordResetToken reset = new PasswordResetToken();
        reset.setOwner(user);
        reset.setTokenHash(sha256("test-reset-secret"));
        reset.setCreatedAt(clock.instant());
        reset.setExpiresAt(clock.instant().plusSeconds(300));
        resets.save(reset);
        passwordReset.resetPassword("test-reset-secret", "new-test-password");
        assertThat(tokens.count()).isZero();
        assertThat(challenges.count()).isZero();
        account(first, 401);
        code(pending, latestCode(), 401);
        signIn(EMAIL, PASSWORD, 401);
    }

    @Test void deletingAccountRemovesCredentialRowsBeforeOwner() throws Exception {
        AppUser user = user();
        String token = signIn().get("token").asText();
        user.setTwoFactorMethod(TwoFactorMethod.EMAIL);
        users.save(user);
        signIn();
        accounts.deleteAccount(EMAIL, PASSWORD);
        assertThat(tokens.count()).isZero();
        assertThat(challenges.count()).isZero();
        assertThat(users.count()).isZero();
        account(token, 401);
    }

    @Test void cleanupRemovesOnlyExpiredCredentials() throws Exception {
        AppUser user = user();
        signIn();
        AppToken expired = tokens.findAll().getFirst();
        expired.setExpiresAt(clock.instant());
        tokens.save(expired);
        String live = signIn().get("token").asText();
        user.setEmailVerified(false);
        users.save(user);
        signIn();
        var challenge = challenges.findAll().getFirst();
        challenge.setExpiresAt(clock.instant());
        challenges.save(challenge);
        tokenService.purgeExpired();
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(challenges.count()).isZero();
        account(live, 200);
        signIn();
        tokenService.purgeExpired();
        assertThat(challenges.count()).isEqualTo(1);
    }

    @Test void parallelChallengeConsumptionIssuesOneToken() throws Exception {
        AppUser user = user();
        user.setEmailVerified(false);
        users.save(user);
        String challenge = signIn().get("challenge").asText();
        String correct = latestCode();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> consume = () -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/code").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("challenge", challenge, "code", correct))))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(consume);
            var second = pool.submit(consume);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test void invalidJsonAndOversizedCredentialsAreNotEchoed() throws Exception {
        postJson("/auth/sign-in", Map.of("email", EMAIL, "password", "x".repeat(73)), 400);
        postJson("/auth/sign-in", Map.of("email", EMAIL, "password", "é".repeat(37)), 400);
        mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"error\":\"invalid\"}"));
    }

    @Test void authenticationRequestsDoNotLogBearerOrChallengeSecrets() throws Exception {
        AppUser user = user();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            String token = signIn().get("token").asText();
            account(token, 200);
            user.setEmailVerified(false);
            users.save(user);
            String challenge = signIn().get("challenge").asText();
            code(challenge, "bad", 400);
            String logs = capture.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).doesNotContain(token, challenge, "Bearer " + token, PASSWORD);
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }


    @Test void browserPasswordSuccessCannotResetApiCodeFailures() throws Exception {
        enableApp(user(), new ArrayList<>());
        for (int i = 0; i < 5; i++) {
            String pending = signIn().get("challenge").asText();
            code(pending, "bad", i == 4 ? 429 : 400);
            if (i < 4) mvc.perform(post("/login").with(csrf()).param("username", EMAIL).param("password", PASSWORD))
                    .andExpect(redirectedUrl("/sign-in/code"));
        }
        signIn(EMAIL, PASSWORD, 429);
    }

    @Test void browserAndApiCannotBothConsumeOneAppCode() throws Exception { raceSharedCode(true, false); }
    @Test void browserAndApiCannotBothConsumeOneRecoveryCode() throws Exception { raceSharedCode(true, true); }
    @Test void browserAndApiCannotBothConsumeOneEmailCode() throws Exception { raceSharedCode(false, false); }

    private void raceSharedCode(boolean app, boolean recoveryCode) throws Exception {
        AppUser user = user();
        List<String> recoveryCodes = new ArrayList<>();
        byte[] key = null;
        if (app) key = enableApp(user, recoveryCodes);
        else {
            user.setTwoFactorMethod(TwoFactorMethod.EMAIL);
            users.save(user);
        }
        var browserLogin = mvc.perform(post("/login").with(csrf()).param("username", EMAIL).param("password", PASSWORD))
                .andExpect(redirectedUrl("/sign-in/code")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) browserLogin.getRequest().getSession(false);
        String pending = signIn().get("challenge").asText();
        String shared = recoveryCode ? recoveryCodes.getFirst()
                : app ? Totp.code(key, clock.instant().getEpochSecond() / 30) : latestCode();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var browser = pool.submit(() -> {
                start.await();
                var result = mvc.perform(post("/sign-in/code").session(session).with(csrf()).param("code", shared))
                        .andReturn().getResponse();
                return "/".equals(result.getRedirectedUrl()) || "/?recovery-used".equals(result.getRedirectedUrl());
            });
            var api = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/code").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("challenge", pending, "code", shared))))
                        .andReturn().getResponse().getStatus() == 200;
            });
            start.countDown();
            assertThat(List.of(browser.get(15, TimeUnit.SECONDS), api.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }


    private void resetLink(AppUser user, String secret) throws Exception {
        PasswordResetToken link = new PasswordResetToken();
        link.setOwner(user);
        link.setTokenHash(sha256(secret));
        link.setCreatedAt(clock.instant());
        link.setExpiresAt(clock.instant().plusSeconds(300));
        resets.save(link);
    }

    @Test void staleTwoFactorAccountCannotRestoreAnOldPassword() throws Exception {
        AppUser stale = user();
        byte[] key = enableApp(stale, new ArrayList<>());
        resetLink(stale, "stale-account-reset");
        passwordReset.resetPassword("stale-account-reset", "new-test-password");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> twoFactor.disable(stale, PASSWORD,
                Totp.code(key, clock.instant().getEpochSecond() / 30)))
                .isInstanceOf(com.angel.flexbuddy.exception.InvalidAccountPasswordException.class);
        assertThat(encoder.matches("new-test-password", users.findById(stale.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void emailVerificationRefreshesAnAccountLoadedBeforePasswordReset() throws Exception {
        AppUser user = user();
        user.setEmailVerified(false);
        users.save(user);
        signIn();
        String correct = latestCode();
        resetLink(user, "verify-account-reset");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            entityManager.find(AppUser.class, user.getId()); // This transaction retains the old account in its identity map.
            try (var pool = Executors.newSingleThreadExecutor()) {
                pool.submit(() -> passwordReset.resetPassword("verify-account-reset", "new-test-password"))
                        .get(15, TimeUnit.SECONDS);
            } catch (Exception e) { throw new IllegalStateException(e); }
            assertThat(verification.verify(user.getId(), correct)).isEqualTo(EmailCodeService.CheckResult.OK);
        });
        assertThat(encoder.matches("new-test-password", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void challengeExpiryIsRecheckedAfterWaitingForAccountLock() throws Exception {
        AppUser user = user();
        user.setEmailVerified(false);
        users.save(user);
        String pending = signIn().get("challenge").asText();
        String correct = latestCode();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var request = new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                users.lockById(user.getId()).orElseThrow();
                request.set(pool.submit(() -> mvc.perform(post("/api/v1/auth/code").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("challenge", pending, "code", correct))))
                        .andReturn().getResponse().getStatus()));
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> request.get().get(500, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                clock.advance(Duration.ofMinutes(10));
            });
            assertThat(request.get().get(15, TimeUnit.SECONDS)).isEqualTo(401);
        }
        assertThat(tokens.count()).isZero();
    }

    @Test void resetWaitsForIssuanceThenRevokesTheJustIssuedToken() throws Exception {
        AppUser user = user();
        resetLink(user, "racing-reset");
        var checkingPassword = new CountDownLatch(1);
        var releasePassword = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            checkingPassword.countDown();
            if (!releasePassword.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("test password barrier timed out");
            return invocation.callRealMethod();
        }).when(encoder).matches(org.mockito.ArgumentMatchers.eq(PASSWORD), org.mockito.ArgumentMatchers.anyString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var signingIn = pool.submit(() -> signIn());
            assertThat(checkingPassword.await(15, TimeUnit.SECONDS)).isTrue();
            var resetting = pool.submit(() -> passwordReset.resetPassword("racing-reset", "new-test-password"));
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> resetting.get(500, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally { releasePassword.countDown(); }
            String issued = signingIn.get(15, TimeUnit.SECONDS).get("token").asText();
            resetting.get(15, TimeUnit.SECONDS);
            account(issued, 401);
            assertThat(tokens.count()).isZero();
        } finally {
            releasePassword.countDown();
            org.mockito.Mockito.doCallRealMethod().when(encoder).matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
