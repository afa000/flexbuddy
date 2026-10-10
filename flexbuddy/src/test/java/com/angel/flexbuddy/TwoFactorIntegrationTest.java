package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.mail.EmailCodeMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.EmailCodePurpose;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.EmailCodeRepository;
import com.angel.flexbuddy.repository.RecoveryCodeRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.security.Totp;
import com.angel.flexbuddy.service.TwoFactorService;

import jakarta.servlet.http.Cookie;

/** Two-step sign-in through the real security chain and database, with a clock the tests move to compute app codes. */
@SpringBootTest(properties = "flexbuddy.security.remember-me-key=two-factor-it-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorIntegrationTest {

    private static final String EMAIL = "twostep@example.com";
    private static final String PASSWORD = "right-password-1";

    static class CapturingMailer implements EmailCodeMailer {
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
            return new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
        }

        @Bean
        @Primary
        CapturingMailer capturingCodeMailer() {
            return new CapturingMailer();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private EmailCodeRepository emailCodeRepository;

    @Autowired
    private RecoveryCodeRepository recoveryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttemptLimiter limiter;

    @Autowired
    private TwoFactorService twoFactor;

    @Autowired
    private CapturingMailer mailer;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists persistent_logins");
        jdbcTemplate.execute("""
                create table persistent_logins (
                    username varchar(254) not null,
                    series varchar(64) primary key,
                    token varchar(64) not null,
                    last_used timestamp not null
                )
                """);
        wipe();
        mailer.codes.clear();
        clock.advance(Duration.between(clock.instant(), Instant.parse("2026-10-10T12:00:00Z")));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from persistent_logins");
        wipe();
    }

    private void wipe() {
        recoveryRepository.deleteAll();
        emailCodeRepository.deleteAll();
        userRepository.deleteAll();
        limiter.clearAll();
    }

    private long step() {
        return clock.instant().getEpochSecond() / Totp.STEP_SECONDS;
    }

    /** A driver with the app method on; returns the secret's bytes and fills the recovery codes. */
    private byte[] userWithApp(List<String> recovery) {
        AppUser user = userRepository.save(new AppUser("Two Step", EMAIL, passwordEncoder.encode(PASSWORD)));
        TwoFactorService.AppSetup setup = twoFactor.beginApp(user);
        byte[] key = Totp.fromBase32(setup.secret());
        recovery.addAll(twoFactor.confirmApp(user, setup.secret(), Totp.code(key, step())));
        // A later step, so the code used to turn it on cannot be mistaken for a replay when signing in.
        clock.advance(Duration.ofSeconds(35));
        return key;
    }

    private void userWithEmail() {
        AppUser user = userRepository.save(new AppUser("Two Step", EMAIL, passwordEncoder.encode(PASSWORD)));
        twoFactor.beginEmail(user, "198.51.100.1");
        twoFactor.confirmEmail(user, mailer.codes.get(0));
        mailer.codes.clear();
    }

    private MockHttpServletRequestBuilder signIn(String password, boolean remember) {
        MockHttpServletRequestBuilder request = post("/login").secure(true).with(csrf())
                .param("username", EMAIL).param("password", password);
        return remember ? request.param("remember-me", "on") : request;
    }

    private MockHttpServletRequestBuilder enter(String code, MockHttpSession session) {
        return post("/sign-in/code").session(session).secure(true).with(csrf()).param("code", code);
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static Cookie rememberCookie(MvcResult result) {
        Cookie last = null;
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (SecurityConfig.REMEMBER_ME_COOKIE.equals(cookie.getName())) {
                last = cookie;
            }
        }
        return last != null && last.getValue() != null && !last.getValue().isEmpty() && last.getMaxAge() != 0 ? last : null;
    }

    @Test
    void aDriverWithoutTwoStepSignsStraightIn() throws Exception {
        userRepository.save(new AppUser("Plain", EMAIL, passwordEncoder.encode(PASSWORD)));

        mockMvc.perform(signIn(PASSWORD, false)).andExpect(redirectedUrl("/"));
    }

    @Test
    void anAppDriverMustEnterTheCodeBeforeAnythingOpens() throws Exception {
        byte[] key = userWithApp(new ArrayList<>());

        MvcResult result = mockMvc.perform(signIn(PASSWORD, true)).andExpect(redirectedUrl("/sign-in/code")).andReturn();

        assertThat(rememberCookie(result)).isNull();
        MockHttpSession pending = sessionOf(result);
        mockMvc.perform(get("/").session(pending).secure(true)).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/sign-in/code").session(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Enter your code")))
                .andExpect(content().string(Matchers.containsString("authenticator app")));

        mockMvc.perform(enter(Totp.code(key, step()), pending)).andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/").session(pending).secure(true)).andExpect(status().isOk());
    }

    @Test
    void aCodeThatWasJustUsedCannotSignInAgain() throws Exception {
        byte[] key = userWithApp(new ArrayList<>());
        String code = Totp.code(key, step());
        MockHttpSession first = sessionOf(mockMvc.perform(signIn(PASSWORD, false)).andReturn());
        mockMvc.perform(enter(code, first)).andExpect(redirectedUrl("/"));

        MockHttpSession second = sessionOf(mockMvc.perform(signIn(PASSWORD, false)).andReturn());
        mockMvc.perform(enter(code, second))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("That code isn&#39;t right.")));
    }

    @Test
    void aTrustedDeviceSkipsTheSecondStepAfterTheFirstOne() throws Exception {
        byte[] key = userWithApp(new ArrayList<>());
        MockHttpSession pending = sessionOf(mockMvc.perform(signIn(PASSWORD, true)).andReturn());

        MvcResult done = mockMvc.perform(enter(Totp.code(key, step()), pending)).andExpect(redirectedUrl("/")).andReturn();

        Cookie cookie = rememberCookie(done);
        assertThat(cookie).isNotNull();
        // A new session carrying only the cookie, which is how the Play app reopens, needs no code.
        mockMvc.perform(get("/").secure(true).cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void anEmailDriverIsSentACodeThatSignsThemIn() throws Exception {
        userWithEmail();

        MvcResult result = mockMvc.perform(signIn(PASSWORD, false)).andExpect(redirectedUrl("/sign-in/code")).andReturn();

        assertThat(mailer.codes).hasSize(1);
        MockHttpSession pending = sessionOf(result);
        mockMvc.perform(get("/sign-in/code").session(pending))
                .andExpect(content().string(Matchers.containsString("t••••@example.com")));
        mockMvc.perform(enter(mailer.codes.get(0), pending)).andExpect(redirectedUrl("/"));
    }

    @Test
    void aPendingSignInExpiresAfterTenMinutes() throws Exception {
        userWithApp(new ArrayList<>());
        MockHttpSession pending = sessionOf(mockMvc.perform(signIn(PASSWORD, false)).andReturn());

        clock.advance(Duration.ofMinutes(11));

        mockMvc.perform(get("/sign-in/code").session(pending)).andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void fiveWrongCodesLockTheSignIn() throws Exception {
        userWithApp(new ArrayList<>());
        MockHttpSession pending = sessionOf(mockMvc.perform(signIn(PASSWORD, false)).andReturn());

        for (int wrong = 1; wrong <= 4; wrong++) {
            mockMvc.perform(enter("000000", pending)).andExpect(status().isOk());
        }
        mockMvc.perform(enter("000000", pending)).andExpect(redirectedUrl("/login?locked"));

        mockMvc.perform(get("/sign-in/code").session(pending)).andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void aRecoveryCodeSignsInAndTheHomePageIsToldAboutIt() throws Exception {
        List<String> recovery = new ArrayList<>();
        userWithApp(recovery);
        MockHttpSession pending = sessionOf(mockMvc.perform(signIn(PASSWORD, false)).andReturn());

        mockMvc.perform(enter(recovery.get(0), pending)).andExpect(redirectedUrl("/?recovery-used"));

        mockMvc.perform(get("/").session(pending).secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-recovery-left=\"9\"")));
    }

    @Test
    void settingsTurnTheAppOnShowTheRecoveryCodesOnceAndNeverAgain() throws Exception {
        AppUser user = userRepository.save(new AppUser("Two Step", EMAIL, passwordEncoder.encode(PASSWORD)));
        MockHttpSession session = new MockHttpSession();

        MvcResult begin = mockMvc.perform(post("/account/two-factor/app").session(session).with(user(EMAIL)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<svg")))
                .andExpect(content().string(Matchers.containsString("otpauth://totp/FlexBuddy:")))
                .andReturn();
        String secret = (String) begin.getRequest().getSession().getAttribute("flexbuddy.twoFactorSetupSecret");
        assertThat(secret).isNotBlank();

        MvcResult confirmed = mockMvc.perform(post("/account/two-factor/app/confirm").session(session).with(user(EMAIL)).with(csrf())
                        .param("code", Totp.code(Totp.fromBase32(secret), step())))
                .andExpect(status().isOk())
                .andReturn();
        String page = confirmed.getResponse().getContentAsString();
        assertThat(page.split("data-recovery-code", -1).length - 1).isEqualTo(10);

        String later = mockMvc.perform(get("/account/two-factor").session(session).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Two-step sign-in is on")))
                .andReturn().getResponse().getContentAsString();
        assertThat(later).doesNotContain("data-recovery-code");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getTotpSecret()).doesNotContain(secret);
    }
}
