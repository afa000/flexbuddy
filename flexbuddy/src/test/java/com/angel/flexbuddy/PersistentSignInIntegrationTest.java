package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;

@SpringBootTest(properties = "flexbuddy.security.remember-me-key=integration-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersistentSignInIntegrationTest {

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