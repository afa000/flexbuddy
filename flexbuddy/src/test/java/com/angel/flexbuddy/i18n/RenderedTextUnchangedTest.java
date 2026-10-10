package com.angel.flexbuddy.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.EmailCodeRepository;
import com.angel.flexbuddy.repository.RecoveryCodeRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

/**
 * The pages read the same English as before the text moved into {@code i18n/messages.properties}: a handful of exact
 * strings from every group of keys, and no {@code ??key??} where a key is missing.
 */
@SpringBootTest(properties = {"flexbuddy.security.remember-me-key=rendered-text-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RenderedTextUnchangedTest {

    private static final String EMAIL = "text@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodes;

    @Autowired
    private EmailCodeRepository emailCodes;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void seed() throws Exception {
        wipe();
        userRepository.save(new AppUser("Text Tester", EMAIL, encoder.encode("text-password-1")));
        mockMvc.perform(post("/shifts").with(user(EMAIL)).with(csrf()).contentType("application/json")
                .content("{\"station\":\"VEA7\",\"date\":\"2026-05-02\",\"startTime\":\"09:00\",\"endTime\":\"13:00\","
                        + "\"basePay\":80,\"tips\":10,\"miles\":42.5}"));
    }

    @AfterEach
    void wipe() {
        shiftRepository.deleteAll();
        recoveryCodes.deleteAll();
        emailCodes.deleteAll();
        userRepository.deleteAll();
    }

    private String render(MockHttpServletRequestBuilder request) throws Exception {
        String html = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
        assertThat(html).as("a key the page uses is missing from the message file").doesNotContain("??");
        return html;
    }

    @Test
    void theSignInPageReadsAsBefore() throws Exception {
        assertThat(render(get("/login"))).contains("<html lang=\"en\"", "Sign in to FlexBuddy.", "Forgot password?",
                "Keep me signed in", "Stay signed in on this device for 30 days.", "Shift tracker");
    }

    @Test
    void theSignInNoticesReadAsBefore() throws Exception {
        assertThat(render(get("/login").param("locked", ""))).contains("Too many sign-in attempts. Wait 15 minutes, or");
        assertThat(render(get("/login").param("error", ""))).contains("The email or password was incorrect.");
        assertThat(render(get("/login").param("logout", ""))).contains("You have signed out.");
    }

    @Test
    void theSignUpFormAndItsErrorsReadAsBefore() throws Exception {
        assertThat(render(get("/register"))).contains("Create account");
        assertThat(render(post("/register").with(csrf()).param("displayName", "").param("email", "bad")
                .param("password", "short")))
                .contains("Enter your name.", "Enter a valid email address.",
                        "Your password must be between 8 and 72 characters.");
    }

    @Test
    void theForgotPasswordPageReadsAsBefore() throws Exception {
        assertThat(render(get("/forgot-password"))).contains("Send reset link");
        assertThat(render(post("/forgot-password").with(csrf()).param("email", "not-an-email")))
                .contains("Enter a valid email address.");
    }

    @Test
    void homeReadsAsBefore() throws Exception {
        assertThat(render(get("/").with(user(EMAIL)))).contains("Hi, Text Tester", "Needs attention", "Recent blocks",
                "Evaluate a block");
    }

    @Test
    void accountReadsAsBefore() throws Exception {
        assertThat(render(get("/account").with(user(EMAIL)))).contains("Earnings &amp; costs", "Save reminders",
                "Delete account permanently", "Sign-in methods", "Last backup: Never", "<b>Set</b>");
    }

    @Test
    void theTaxSummaryReadsAsBefore() throws Exception {
        assertThat(render(get("/tax/year-summary").param("year", "2026").with(user(EMAIL))))
                .contains("Tax year summary 2026 | FlexBuddy", "Tax year summary · 2026", "Standard mileage",
                        "Earnings, miles and expenses by month for 2026", "No expenses recorded for 2026.");
    }

    @Test
    void theErrorPageReadsAsBefore() throws Exception {
        assertThat(render(get("/error").accept(org.springframework.http.MediaType.TEXT_HTML)
                .requestAttr("jakarta.servlet.error.status_code", 404)))
                .contains("Page not found", "ERROR 404", "The page you requested does not exist or may have moved.",
                        "subject=FlexBuddy%20problem%20report",
                        "What%20were%20you%20doing%20when%20this%20happened?%20(error%20404)");
        assertThat(render(get("/error").accept(org.springframework.http.MediaType.TEXT_HTML)
                .requestAttr("jakarta.servlet.error.status_code", 500)))
                .contains("Something went wrong");
    }

    @Test
    void theLegalPagesKeepTheirTextInEnglishWhoeverReadsThem() throws Exception {
        assertThat(render(get("/privacy"))).contains("<html lang=\"en\"", "lang=\"en\" aria-labelledby=\"privacy-title\"",
                "Privacy policy | FlexBuddy", "Sign in");
        assertThat(render(get("/terms").with(user(EMAIL)))).contains("Terms of use | FlexBuddy", "Back to FlexBuddy");
    }

    @Test
    void aMissingShiftAnswersInEnglish() throws Exception {
        mockMvc.perform(get("/shifts/999999").with(user(EMAIL)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("Shift not found with id: 999999"));
    }

    @Test
    void aBrowserThatAsksForFrenchStillGetsEnglishBecauseOnlyEnglishIsSupported() throws Exception {
        mockMvc.perform(get("/shifts/999999").with(user(EMAIL)).header("Accept-Language", "fr"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("Shift not found with id: 999999"));
        assertThat(render(get("/login").header("Accept-Language", "fr-FR,fr;q=0.9")))
                .contains("<html lang=\"en\"", "Forgot password?");
    }
}
