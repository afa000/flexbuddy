package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.mail.PasswordResetMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PasswordResetTokenRepository;
import com.angel.flexbuddy.security.AttemptLimiter;

/** The whole flow through the real security chain and database, with a mailer that keeps what it was asked to send. */
@SpringBootTest(properties = "flexbuddy.security.remember-me-key=reset-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    private static final String EMAIL = "reset@example.com";
    private static final String OLD_PASSWORD = "old-password-1";
    private static final String NEW_PASSWORD = "new-password-2";

    /** Keeps each link instead of emailing it. Runs on the calling thread, so the test never has to wait. */
    static class CapturingMailer implements PasswordResetMailer {
        final List<String> links = new ArrayList<>();

        @Override
        public void sendResetLink(String toEmail, String displayName, String link) {
            links.add(link);
        }
    }

    @TestConfiguration
    static class MailConfig {
        @Bean
        @Primary
        CapturingMailer capturingMailer() {
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
    private PasswordResetTokenRepository tokenRepository;

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
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new AppUser("Reset Test", EMAIL, passwordEncoder.encode(OLD_PASSWORD)));
        limiter.clearAll();
        mailer.links.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from persistent_logins");
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        limiter.clearAll();
    }

    private MockHttpServletRequestBuilder login(String password) {
        return post("/login").secure(true).with(csrf()).param("username", EMAIL).param("password", password);
    }

    private MockHttpServletRequestBuilder forgot(String email, String address) {
        return post("/forgot-password").with(csrf()).with(request -> {
            request.setRemoteAddr(address);
            return request;
        }).param("email", email);
    }

    private String tokenFrom(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    @Test
    void aForgottenPasswordCanBeResetOnceAndTheNewOneSignsIn() throws Exception {
        mockMvc.perform(forgot(EMAIL, "198.51.100.5"))
                .andExpect(redirectedUrl("/forgot-password?sent"));
        assertThat(mailer.links).hasSize(1);
        String token = tokenFrom(mailer.links.get(0));

        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Choose a new password")));

        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("token", token).param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login?reset"));

        mockMvc.perform(login(OLD_PASSWORD)).andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(login(NEW_PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(EMAIL));

        // The link is used up.
        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(content().string(Matchers.containsString("This link has expired or was already used")));
        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("token", token).param("password", "another-password-3").param("confirmPassword", "another-password-3"))
                .andExpect(content().string(Matchers.containsString("This link has expired or was already used")));
    }

    @Test
    void anUnknownEmailGetsTheSamePageAndNoEmail() throws Exception {
        mockMvc.perform(forgot("nobody@example.com", "198.51.100.5"))
                .andExpect(redirectedUrl("/forgot-password?sent"));

        assertThat(mailer.links).isEmpty();
    }

    @Test
    void askingAgainCancelsTheEarlierLink() throws Exception {
        mockMvc.perform(forgot(EMAIL, "198.51.100.5"));
        mockMvc.perform(forgot(EMAIL, "198.51.100.5"));
        assertThat(mailer.links).hasSize(2);

        mockMvc.perform(get("/reset-password").param("token", tokenFrom(mailer.links.get(0))))
                .andExpect(content().string(Matchers.containsString("This link has expired or was already used")));
        mockMvc.perform(get("/reset-password").param("token", tokenFrom(mailer.links.get(1))))
                .andExpect(content().string(Matchers.containsString("Choose a new password")));
    }

    @Test
    void onlyThreeEmailsAnHourGoToOneAddressAndThePageNeverChanges() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(forgot(EMAIL, "198.51.100." + (10 + i)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/forgot-password?sent"));
        }

        assertThat(mailer.links).hasSize(3);
    }

    @Test
    void resettingLiftsAnActiveSignInLock() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("wrong-password"));
        }
        mockMvc.perform(login(OLD_PASSWORD)).andExpect(redirectedUrl("/login?locked"));

        mockMvc.perform(forgot(EMAIL, "198.51.100.5"));
        String token = tokenFrom(mailer.links.get(0));
        mockMvc.perform(post("/reset-password").with(csrf())
                .param("token", token).param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD));

        mockMvc.perform(login(NEW_PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void resettingSignsOutADeviceThatStayedSignedIn() throws Exception {
        var signedIn = mockMvc.perform(login(OLD_PASSWORD).param("remember-me", "on"))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        var cookie = signedIn.getResponse().getCookie(com.angel.flexbuddy.config.SecurityConfig.REMEMBER_ME_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from persistent_logins", Integer.class)).isOne();

        mockMvc.perform(forgot(EMAIL, "198.51.100.5"));
        mockMvc.perform(post("/reset-password").with(csrf())
                .param("token", tokenFrom(mailer.links.get(0))).param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD));

        assertThat(jdbcTemplate.queryForObject("select count(*) from persistent_logins", Integer.class)).isZero();
        mockMvc.perform(get("/account").secure(true).cookie(cookie))
                .andExpect(redirectedUrl("/login"));
    }
}
