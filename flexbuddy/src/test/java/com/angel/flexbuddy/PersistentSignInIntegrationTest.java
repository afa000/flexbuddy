package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.MutableClock;

@SpringBootTest(properties = "flexbuddy.security.remember-me-key=integration-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersistentSignInIntegrationTest {

    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
        }
    }

    private static final String EMAIL = "remember@example.com";
    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttemptLimiter attemptLimiter;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        // The counts are in memory and shared by every test in this Spring context, so one test's failures must not lock another.
        attemptLimiter.clearAll();
        jdbcTemplate.execute("drop table if exists persistent_logins");
        jdbcTemplate.execute("""
                create table persistent_logins (
                    username varchar(254) not null,
                    series varchar(64) primary key,
                    token varchar(64) not null,
                    last_used timestamp not null
                )
                """);
        userRepository.deleteAll();
        userRepository.save(new AppUser("Remember Test", EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from persistent_logins");
        userRepository.deleteAll();
    }

    @Test
    void loginWithoutCheckboxDoesNotCreatePersistentSignIn() throws Exception {
        MvcResult result = login(false);

        assertThat(result.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE)).isNull();
        assertThat(tokenCount()).isZero();
    }

    @Test
    void rememberMeCookieAuthenticatesAfterSessionIsDropped() throws Exception {
        MvcResult login = login(true);
        Cookie cookie = login.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE);

        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(SecurityConfig.REMEMBER_ME_VALIDITY_SECONDS);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(tokenCount()).isOne();

        mockMvc.perform(get("/account").secure(true).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void concurrentRequestsWithTheSameRememberMeCookieKeepTheUserSignedIn() throws Exception {
        MvcResult login = login(true);
        Cookie cookie = login.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE);

        // An app launch sends the page and service worker requests together, before either sees a rotated cookie.
        mockMvc.perform(get("/").secure(true).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL));
        mockMvc.perform(get("/account").secure(true).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL));

        assertThat(tokenCount()).isOne();
    }

    @Test
    void logoutRevokesCurrentPersistentToken() throws Exception {
        MvcResult login = login(true);
        Cookie cookie = login.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        assertThat(tokenCount()).isOne();

        MvcResult logout = mockMvc.perform(post("/logout")
                        .secure(true)
                        .session(session)
                        .cookie(cookie)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andReturn();

        assertThat(tokenCount()).isZero();
        assertThat(logout.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE).getMaxAge()).isZero();
        mockMvc.perform(get("/account").secure(true).cookie(cookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
    @Test
    void signOutEverywhereRevokesEveryTokenAndInvalidatesCurrentSession() throws Exception {
        MvcResult firstLogin = login(true);
        MvcResult secondLogin = login(true);
        Cookie secondCookie = secondLogin.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE);
        MockHttpSession firstSession = (MockHttpSession) firstLogin.getRequest().getSession(false);

        assertThat(tokenCount()).isEqualTo(2);

        mockMvc.perform(post("/account/sign-out-everywhere")
                        .secure(true)
                        .session(firstSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?everywhere"));

        assertThat(tokenCount()).isZero();
        assertThat(firstSession.isInvalid()).isTrue();
        mockMvc.perform(get("/account").secure(true).cookie(secondCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void reusedCookieAfterTheGracePeriodSendsAPageToSignInWithTheExpiredNotice() throws Exception {
        Cookie staleCookie = staleCookie();

        MvcResult result = mockMvc.perform(get("/").secure(true).accept(MediaType.TEXT_HTML).cookie(staleCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"))
                .andExpect(unauthenticated())
                .andReturn();

        assertCookieClearedAndTokensRemoved(result);
    }

    @Test
    void reusedCookieDoesNotBreakPublicPagesAndStaticFiles() throws Exception {
        for (String path : new String[] {"/login", "/sw.js", "/css/styles.css"}) {
            Cookie staleCookie = staleCookie();

            MvcResult result = mockMvc.perform(get(path).secure(true).accept(MediaType.TEXT_HTML).cookie(staleCookie))
                    .andExpect(status().isOk())
                    .andReturn();

            assertCookieClearedAndTokensRemoved(result);
        }
    }

    @Test
    void reusedCookieOnAProtectedJsonRequestGetsTheSameAnswerAsNoCookie() throws Exception {
        MvcResult withoutCookie = mockMvc.perform(get("/shifts").secure(true).accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Cookie staleCookie = staleCookie();

        MvcResult withStaleCookie = mockMvc
                .perform(get("/shifts").secure(true).accept(MediaType.APPLICATION_JSON).cookie(staleCookie))
                .andReturn();

        assertThat(withStaleCookie.getResponse().getStatus()).isEqualTo(withoutCookie.getResponse().getStatus());
        assertThat(withStaleCookie.getResponse().getRedirectedUrl())
                .isEqualTo(withoutCookie.getResponse().getRedirectedUrl());
        assertCookieClearedAndTokensRemoved(withStaleCookie);
    }

    @Test
    void sessionExpiredNoticeShowsInEnglishAndSpanish() throws Exception {
        mockMvc.perform(get("/login").param("expired", "").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your session expired. Sign in again.")));
        mockMvc.perform(get("/login").param("expired", "").param("lang", "es").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Su sesi\u00f3n venci\u00f3. Vuelva a iniciar sesi\u00f3n.")));
    }

    /** Signs in, rotates the cookie once, and moves past the grace period so the first cookie is stale. */
    private Cookie staleCookie() throws Exception {
        jdbcTemplate.update("delete from persistent_logins");
        Cookie original = login(true).getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE);
        mockMvc.perform(get("/account").secure(true).cookie(original)).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(2));
        return original;
    }

    private void assertCookieClearedAndTokensRemoved(MvcResult result) {
        Cookie cleared = result.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(tokenCount()).isZero();
    }

    private MvcResult login(boolean rememberMe) throws Exception {
        var request = post("/login")
                .secure(true)
                .with(csrf())
                .param("username", EMAIL)
                .param("password", PASSWORD);
        if (rememberMe) {
            request.param("remember-me", "on");
        }
        return mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
    }

    private int tokenCount() {
        return jdbcTemplate.queryForObject("select count(*) from persistent_logins", Integer.class);
    }
}