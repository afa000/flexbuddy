package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.angel.flexbuddy.security.AttemptLimiter;

import jakarta.servlet.http.Cookie;

/** Sign-up and sign-in with an emailed code, through the real security chain and database. */
@SpringBootTest(properties = "flexbuddy.security.remember-me-key=verify-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationIntegrationTest {

    private static final String EMAIL = "reset@example.com";
    private static final String PASSWORD = "right-password-1";

    /** Keeps each code instead of emailing it. Runs on the calling thread, so the test never has to wait. */
    static class CapturingMailer implements EmailCodeMailer {
        final List<String> codes = new ArrayList<>();

        @Override
        public void sendCode(String toEmail, String displayName, String code, EmailCodePurpose purpose) {
            codes.add(code);
        }
    }

    @TestConfiguration
    static class MailConfig {
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
    private EmailCodeRepository codeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttemptLimiter limiter;

    @Autowired
    private CapturingMailer mailer;

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
        codeRepository.deleteAll();
        userRepository.deleteAll();
        limiter.clearAll();
        mailer.codes.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from persistent_logins");
        codeRepository.deleteAll();
        userRepository.deleteAll();
        limiter.clearAll();
    }

    private MockHttpServletRequestBuilder signUp(String displayMode, MockHttpSession session) {
        return post("/register").session(session).with(csrf())
                .param("displayName", "Pat Driver").param("email", EMAIL).param("password", PASSWORD)
                .param("app-display-mode", displayMode);
    }

    private MockHttpServletRequestBuilder signIn(String password, boolean remember, MockHttpSession session) {
        MockHttpServletRequestBuilder request = post("/login").session(session).secure(true).with(csrf())
                .param("username", EMAIL).param("password", password);
        return remember ? request.param("remember-me", "on") : request;
    }

    private MockHttpServletRequestBuilder enter(String code, MockHttpSession session) {
        return post("/verify-email").session(session).with(csrf()).param("code", code);
    }

    private static boolean hasRememberMe(MvcResult result) {
        // A browser keeps the last cookie of a name, and form login's cookie is cancelled by a later one.
        Cookie last = null;
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (SecurityConfig.REMEMBER_ME_COOKIE.equals(cookie.getName())) {
                last = cookie;
            }
        }
        return last != null && last.getValue() != null && !last.getValue().isEmpty() && last.getMaxAge() != 0;
    }

    @Test
    void signingUpSendsACodeAndTheRightCodeSignsTheDriverIn() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(signUp("browser", session)).andExpect(redirectedUrl("/verify-email"));
        assertThat(mailer.codes).hasSize(1);
        assertThat(userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow().isEmailVerified()).isFalse();

        mockMvc.perform(get("/verify-email").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("r••••@example.com")))
                .andExpect(content().string(Matchers.containsString("autocomplete=\"one-time-code\"")));
        // Nothing but the code page opens before the code is entered.
        mockMvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));

        mockMvc.perform(enter(mailer.codes.get(0), session))
                .andExpect(redirectedUrl("/?welcome"));
        mockMvc.perform(get("/").session(session)).andExpect(status().isOk());
        assertThat(userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void aWrongCodeDoesNotSignAnyoneIn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(signUp("browser", session));
        String wrong = mailer.codes.get(0).equals("000000") ? "111111" : "000000";

        mockMvc.perform(enter(wrong, session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("That code isn&#39;t right")));
        mockMvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void anUnverifiedAccountWithTheRightPasswordGetsANewCodeAndNoSignInOrRememberMe() throws Exception {
        AppUser pending = new AppUser("Pat Driver", EMAIL, passwordEncoder.encode(PASSWORD));
        pending.setEmailVerified(false);
        userRepository.save(pending);
        MockHttpSession session = new MockHttpSession();

        MvcResult result = mockMvc.perform(signIn(PASSWORD, true, session))
                .andExpect(redirectedUrl("/verify-email"))
                .andReturn();

        assertThat(hasRememberMe(result)).isFalse();
        assertThat(mailer.codes).hasSize(1);
        MockHttpSession pendingSession = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/").session(pendingSession)).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/verify-email").session(pendingSession)).andExpect(status().isOk());
    }

    @Test
    void aWrongPasswordOnAnUnverifiedAccountLooksLikeAnyOtherAndSendsNothing() throws Exception {
        AppUser pending = new AppUser("Pat Driver", EMAIL, passwordEncoder.encode(PASSWORD));
        pending.setEmailVerified(false);
        userRepository.save(pending);

        mockMvc.perform(signIn("wrong-password-9", false, new MockHttpSession()))
                .andExpect(redirectedUrl("/login?error"));

        assertThat(mailer.codes).isEmpty();
    }

    @Test
    void anExistingVerifiedAccountSignsInStraightHome() throws Exception {
        userRepository.save(new AppUser("Pat Driver", EMAIL, passwordEncoder.encode(PASSWORD)));

        mockMvc.perform(signIn(PASSWORD, false, new MockHttpSession())).andExpect(redirectedUrl("/"));

        assertThat(mailer.codes).isEmpty();
    }

    @Test
    void signingUpInThePlayAppKeepsTheDriverSignedInAfterVerifying() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(signUp("standalone", session));

        MvcResult result = mockMvc.perform(enter(mailer.codes.get(0), session))
                .andExpect(redirectedUrl("/?welcome"))
                .andReturn();

        assertThat(hasRememberMe(result)).isTrue();
    }

    @Test
    void aBrowserSignUpIsNotRemembered() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(signUp("browser", session));

        MvcResult result = mockMvc.perform(enter(mailer.codes.get(0), session)).andReturn();

        assertThat(hasRememberMe(result)).isFalse();
    }

    @Test
    void signingUpAgainWithAnUnconfirmedAddressReplacesTheEarlierAttempt() throws Exception {
        mockMvc.perform(signUp("browser", new MockHttpSession()));
        MockHttpSession second = new MockHttpSession();

        mockMvc.perform(post("/register").session(second).with(csrf())
                        .param("displayName", "New Name").param("email", EMAIL).param("password", "another-password-2"))
                .andExpect(redirectedUrl("/verify-email"));

        assertThat(userRepository.findAll()).hasSize(1);
        mockMvc.perform(enter(mailer.codes.get(1), second)).andExpect(redirectedUrl("/?welcome"));
        // Only the newer password works.
        mockMvc.perform(post("/login").session(new MockHttpSession()).secure(true).with(csrf())
                        .param("username", EMAIL).param("password", "another-password-2"))
                .andExpect(redirectedUrl("/"));
        mockMvc.perform(post("/login").session(new MockHttpSession()).secure(true).with(csrf())
                        .param("username", EMAIL).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void cancellingClearsThePendingSignUp() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(signUp("browser", session));

        mockMvc.perform(post("/verify-email/cancel").session(session).with(csrf()))
                .andExpect(redirectedUrl("/register"));

        mockMvc.perform(get("/verify-email").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void resendingSendsAFreshCodeUntilTheLimit() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(signUp("browser", session));

        mockMvc.perform(post("/verify-email/resend").session(session).with(csrf()))
                .andExpect(redirectedUrl("/verify-email?resent"));
        assertThat(mailer.codes).hasSize(2);

        for (int resend = 3; resend <= 5; resend++) {
            mockMvc.perform(post("/verify-email/resend").session(session).with(csrf()));
        }
        mockMvc.perform(post("/verify-email/resend").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Too many codes requested")));
        assertThat(mailer.codes).hasSize(5);
    }
}
