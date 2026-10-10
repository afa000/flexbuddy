package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.MutableClock;

/** The sign-in and sign-up limits, run through the real security chain with a clock the tests can move. */
@SpringBootTest(properties = "flexbuddy.security.remember-me-key=limit-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginLimitIntegrationTest {

    private static final String EMAIL = "limits@example.com";
    private static final String PASSWORD = "right-password-1";
    private static final String HOME_ADDRESS = "198.51.100.7";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttemptLimiter limiter;

    @Autowired
    private Clock clock;

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
        userRepository.deleteAll();
        userRepository.save(new AppUser("Limit Test", EMAIL, passwordEncoder.encode(PASSWORD)));
        limiter.clearAll();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from persistent_logins");
        userRepository.deleteAll();
        limiter.clearAll();
    }

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    private MockHttpServletRequestBuilder login(String email, String password, String address) {
        return post("/login")
                .secure(true)
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .param("username", email)
                .param("password", password);
    }

    private void wrongPassword(String email, String address, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            mockMvc.perform(login(email, "wrong-password", address));
        }
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenAgainstTheRightPassword() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(login(EMAIL, "wrong-password", HOME_ADDRESS))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mockMvc.perform(login(EMAIL, "wrong-password", HOME_ADDRESS))
                .andExpect(redirectedUrl("/login?locked"));

        // The password is not even checked now, so the right one gets no further than the wrong ones did.
        mockMvc.perform(login(EMAIL, PASSWORD, HOME_ADDRESS))
                .andExpect(redirectedUrl("/login?locked"))
                .andExpect(unauthenticated());
    }

    @Test
    void theLockLiftsAfter15Minutes() throws Exception {
        wrongPassword(EMAIL, HOME_ADDRESS, 5);
        mockMvc.perform(login(EMAIL, PASSWORD, HOME_ADDRESS)).andExpect(redirectedUrl("/login?locked"));

        clock().advance(Duration.ofMinutes(15));

        mockMvc.perform(login(EMAIL, PASSWORD, HOME_ADDRESS))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void aSuccessfulSignInClearsEarlierFailures() throws Exception {
        wrongPassword(EMAIL, HOME_ADDRESS, 4);
        mockMvc.perform(login(EMAIL, PASSWORD, HOME_ADDRESS)).andExpect(redirectedUrl("/"));

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(login(EMAIL, "wrong-password", HOME_ADDRESS))
                    .andExpect(redirectedUrl("/login?error"));
        }
    }

    @Test
    void unknownEmailsLockTheSameWay() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(login("nobody@example.com", "whatever-password", HOME_ADDRESS))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mockMvc.perform(login("nobody@example.com", "whatever-password", HOME_ADDRESS))
                .andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void emailsCountTogetherWhateverTheirCase() throws Exception {
        wrongPassword("LIMITS@example.com", HOME_ADDRESS, 3);
        wrongPassword(" limits@Example.com", HOME_ADDRESS, 2);

        mockMvc.perform(login(EMAIL, PASSWORD, HOME_ADDRESS)).andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    void oneConnectionGuessingManyEmailsIsLocked() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(login("guess" + i + "@example.com", "wrong-password", "203.0.113.9"));
        }

        mockMvc.perform(login(EMAIL, PASSWORD, "203.0.113.9"))
                .andExpect(redirectedUrl("/login?locked"))
                .andExpect(unauthenticated());
        mockMvc.perform(login(EMAIL, PASSWORD, "203.0.113.10"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void rememberMeStillWorksForALockedEmail() throws Exception {
        MvcResult signedIn = mockMvc.perform(login(EMAIL, PASSWORD, HOME_ADDRESS).param("remember-me", "on"))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        Cookie cookie = signedIn.getResponse().getCookie(SecurityConfig.REMEMBER_ME_COOKIE);
        assertThat(cookie).isNotNull();

        wrongPassword(EMAIL, "198.51.100.99", 5);
        assertThat(limiter.isLocked("login-email", EMAIL)).isTrue();

        mockMvc.perform(get("/account").secure(true).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void theSignInPageSaysHowLongTheLockLasts() throws Exception {
        mockMvc.perform(get("/login").param("locked", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Too many sign-in attempts. Wait <span>15</span> minutes, or")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("reset your password")));
    }

    @Test
    void registrationIsLimitedPerConnection() throws Exception {
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(register("driver" + i + "@example.com", "198.51.100.50"))
                    .andExpect(redirectedUrl("/verify-email"));
        }

        mockMvc.perform(register("driver11@example.com", "198.51.100.50"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Too many sign-ups from this connection")));
        assertThat(userRepository.findByEmailIgnoreCase("driver11@example.com")).isEmpty();

        // Another connection is not affected.
        mockMvc.perform(register("driver12@example.com", "198.51.100.51"))
                .andExpect(redirectedUrl("/verify-email"));
    }

    private MockHttpServletRequestBuilder register(String email, String address) {
        return post("/register")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .param("displayName", "Driver")
                .param("email", email)
                .param("password", "password123");
    }
}
