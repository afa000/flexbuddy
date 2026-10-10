package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.TwoFactorMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.RecoveryCodeRepository;
import com.angel.flexbuddy.security.GoogleRememberChoice;
import com.angel.flexbuddy.security.VerifiedLoginSuccessHandler;

import jakarta.servlet.http.Cookie;

/** Google sign-in with a pretend client registration: the pages, the way out to Google, and what follows the callback. */
@SpringBootTest(properties = {"flexbuddy.google.client-id=test-client", "flexbuddy.google.client-secret=test-secret",
        "flexbuddy.security.remember-me-key=google-it-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleSignInIntegrationTest {

    private static final String EMAIL = "google@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RecoveryCodeRepository recoveryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerifiedLoginSuccessHandler successHandler;

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
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from persistent_logins");
        wipe();
    }

    private void wipe() {
        recoveryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private static OAuth2AuthenticationToken googleLogin(String email) {
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "sub-1", "email", email, "email_verified", true));
        DefaultOidcUser principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token, "email");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private static Cookie rememberCookie(MockHttpServletResponse response) {
        Cookie last = null;
        for (Cookie cookie : response.getCookies()) {
            if (SecurityConfig.REMEMBER_ME_COOKIE.equals(cookie.getName())) {
                last = cookie;
            }
        }
        return last != null && last.getValue() != null && !last.getValue().isEmpty() && last.getMaxAge() != 0 ? last : null;
    }

    @Test
    void theSignInAndSignUpPagesOfferGoogle() throws Exception {
        for (String page : new String[] {"/login", "/register"}) {
            mockMvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("Continue with Google")))
                    .andExpect(content().string(Matchers.containsString("href=\"/oauth2/authorization/google\"")));
        }
    }

    @Test
    void startingGoogleSignInGoesToGoogleAndNotesKeepMeSignedIn() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google").param("remember", "1").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).startsWith("https://accounts.google.com/");
        assertThat(result.getResponse().getRedirectedUrl()).contains("client_id=test-client").contains("scope=openid");
        assertThat(session.getAttribute(GoogleRememberChoice.GOOGLE_REMEMBER)).isNotNull();
    }

    @Test
    void startingWithoutTheChoiceClearsAnyEarlierOne() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GoogleRememberChoice.GOOGLE_REMEMBER, Boolean.TRUE);

        mockMvc.perform(get("/oauth2/authorization/google").session(session)).andExpect(status().is3xxRedirection());

        assertThat(session.getAttribute(GoogleRememberChoice.GOOGLE_REMEMBER)).isNull();
    }

    @Test
    void afterGoogleTheNotedChoiceIssuesRememberMeAndGoesHome() throws Exception {
        userRepository.save(new AppUser("Pat", EMAIL, passwordEncoder.encode("irrelevant-1")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GoogleRememberChoice.GOOGLE_REMEMBER, Boolean.TRUE);
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, googleLogin(EMAIL));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(rememberCookie(response)).isNotNull();
        assertThat(session.getAttribute(GoogleRememberChoice.GOOGLE_REMEMBER)).isNull();
    }

    @Test
    void withoutTheChoiceNoRememberMeTokenIsIssued() throws Exception {
        userRepository.save(new AppUser("Pat", EMAIL, passwordEncoder.encode("irrelevant-1")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, googleLogin(EMAIL));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(rememberCookie(response)).isNull();
    }

    @Test
    void aDriverWithTwoStepOnIsStillAskedForTheCodeAfterGoogle() throws Exception {
        AppUser user = new AppUser("Pat", EMAIL, passwordEncoder.encode("irrelevant-1"));
        user.setTwoFactorMethod(TwoFactorMethod.APP);
        user.setTotpSecret("not-used-here");
        userRepository.save(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GoogleRememberChoice.GOOGLE_REMEMBER, Boolean.TRUE);
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, googleLogin(EMAIL));

        assertThat(response.getRedirectedUrl()).isEqualTo("/sign-in/code");
        assertThat(rememberCookie(response)).isNull();
        // The choice survives into the pending step so the code page can honour it.
        MockHttpSession pending = (MockHttpSession) request.getSession(false);
        assertThat(pending.getAttribute(com.angel.flexbuddy.controller.TwoFactorController.PENDING_REMEMBER)).isEqualTo(true);
    }
}
