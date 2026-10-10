package com.angel.flexbuddy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.exception.InvalidAccountPasswordException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.service.AccountService;
import com.angel.flexbuddy.service.AccountBackupService;
import com.angel.flexbuddy.service.AccountRestoreService;
import com.angel.flexbuddy.service.AccountSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.dto.BackupAccount;
import com.angel.flexbuddy.dto.BackupCounts;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private AccountBackupService backupService;

    @MockitoBean
    private AccountRestoreService restoreService;

    @MockitoBean
    private AccountSettingsService settingsService;

    @MockitoBean
    private com.angel.flexbuddy.service.EmailVerificationService emailVerification;

    @MockitoBean
    private com.angel.flexbuddy.service.EmailCodeService emailCodes;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private AppUserRepository userRepository;

    // Not locked unless a test says so: a mock answers false.
    @MockitoBean
    private AttemptLimiter attemptLimiter;

    @Test
    void loginAndRegistrationPagesArePublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("registration"));
        mockMvc.perform(get("/delete-account"))
                .andExpect(status().isOk())
                .andExpect(view().name("delete-account"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What FlexBuddy deletes")));
    }

    @Test
    void everyPasswordBoxHasARevealButtonAndALoadedScript() throws Exception {
        for (String page : new String[] {"/login", "/register"}) {
            String html = mockMvc.perform(get(page)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(html.split("data-reveal", -1).length - 1).as(page).isEqualTo(1);
            // An empty version would be cached forever by the service worker.
            org.assertj.core.api.Assertions.assertThat(html).containsPattern("/js/password-toggle\\.js\\?v=[^\"]+\"");
        }
        String account = accountPage();
        org.assertj.core.api.Assertions.assertThat(account).containsPattern("type=\"password\"[^>]*data-reveal");
        org.assertj.core.api.Assertions.assertThat(account).containsPattern("/js/password-toggle\\.js\\?v=[^\"]+\"");
    }

    @Test
    void deleteAccountPageSendsVisitorsToSignInAndSignedInDriversToAccount() throws Exception {
        mockMvc.perform(get("/delete-account"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in to delete your account")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/account#account"))));

        mockMvc.perform(get("/delete-account").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/account#account\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Go to Account to delete your account")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/login\""))));
    }

    @Test
    void privacyAndTermsOfferSignInToVisitorsAndAWayBackToSignedInDrivers() throws Exception {
        for (String page : new String[] {"/privacy", "/terms"}) {
            mockMvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Back to FlexBuddy"))));

            mockMvc.perform(get(page).with(user("angel@example.com")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Back to FlexBuddy")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/login\""))));
        }
    }

    @Test
    void privacyAndTermsPagesArePublicAndLinkedFromRegistration() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("privacy"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("How FlexBuddy handles your data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Screenshots you select")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("flexbuddysupport@gmail.com")));

        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(view().name("terms"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Using FlexBuddy")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not affiliated with")));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/privacy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/terms\"")));
    }

    @Test
    void loginPageOffersPersistentSignInForStandaloneMode() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"remember-me\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keep me signed in")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("display-mode: standalone")));
    }

    @Test
    void register_createsAccountAndSendsTheDriverToTheCodePage() throws Exception {
        AppUser created = new AppUser("Angel", "angel@example.com", "hash");
        created.setId(9L);
        when(accountService.register(any(RegistrationRequest.class), any(java.util.Locale.class))).thenReturn(created);
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Angel")
                        .param("email", "angel@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-email"));

        verify(accountService).register(any(RegistrationRequest.class), any(java.util.Locale.class));
        verify(emailVerification).startVerification(org.mockito.ArgumentMatchers.same(created), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void theSignInPageOffersAPasswordResetAndConfirmsOne() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/forgot-password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Forgot password?")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Your password was changed"))));

        mockMvc.perform(get("/login").param("reset", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Your password was changed. Sign in with your new password.")));
        mockMvc.perform(get("/login").param("locked", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("reset your password")));
    }

    @Test
    void dismissingTheSetupCardReturns204AndNamesTheSignedInUser() throws Exception {
        mockMvc.perform(post("/account/setup/dismiss")
                        .with(user("angel@example.com"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(settingsService).dismissSetup("angel@example.com");
    }

    @Test
    void dismissingTheSetupCardNeedsCsrf() throws Exception {
        mockMvc.perform(post("/account/setup/dismiss").with(user("angel@example.com")))
                .andExpect(status().isForbidden());

        verify(settingsService, never()).dismissSetup(any());
    }

    @Test
    void registerShowsTheLimitMessageWhenLocked() throws Exception {
        when(attemptLimiter.isLocked(eq("register-ip"), any())).thenReturn(true);

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Angel")
                        .param("email", "angel@example.com")
                        .param("password", "password123"))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("register"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Too many sign-ups from this connection. Try again in an hour.")));

        verify(accountService, never()).register(any(RegistrationRequest.class), any(java.util.Locale.class));
        verify(attemptLimiter, never()).record(eq("register-ip"), any());
    }

    @Test
    void registerCountsEveryAttemptFromAConnection() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "")
                        .param("email", "not-an-email")
                        .param("password", "short"))
                .andExpect(status().isOk());

        // Even a form that fails validation counts, so it cannot be used to test emails quickly.
        verify(attemptLimiter).record(eq("register-ip"), any());
    }

    @Test
    void register_rejectsAnEmailThatAlreadyExists() throws Exception {
        when(accountService.emailIsRegistered("angel@example.com")).thenReturn(true);

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Angel")
                        .param("email", "angel@example.com")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registration", "email"));

        verify(accountService, never()).register(any(RegistrationRequest.class), any(java.util.Locale.class));
    }

    @Test
    void register_rejectsAnEmailThatWasTakenBetweenTheCheckAndTheInsert() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"))
                .when(accountService).register(any(RegistrationRequest.class), any(java.util.Locale.class));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Angel")
                        .param("email", "angel@example.com")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registration", "email"));
    }

    @Test
    void errorPageRendersABranded404WithoutASession() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/missing-page"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Page not found")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FlexBuddy")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Whitelabel Error Page"))));
    }

    @Test
    void errorPageRendersABrandedServerErrorWithoutASession() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/failed-page"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Something went wrong")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("flexbuddysupport@gmail.com")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Whitelabel Error Page"))));
    }

    @Test
    void accountPageShowsTheDeletionRequirements() throws Exception {
        when(userRepository.findByEmailIgnoreCase("angel@example.com"))
                .thenReturn(Optional.of(new AppUser("Angel", "angel@example.com", "hash")));

        mockMvc.perform(get("/account").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("deletion"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delete account permanently")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_method\" value=\"delete\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/privacy\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/terms\"")));
    }

    @Test
    void accountPageCarriesTheAccountIdAndTheOutboxScript() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(angel));

        mockMvc.perform(get("/account").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<meta name=\"flexbuddy-account\" content=\"42\">")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"outboxAccountRow\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"signOutEverywhereForm\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/outbox.js?v=")));
    }

    private String accountPage() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(angel));
        return mockMvc.perform(get("/account").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void accountPageHasSixFoldedSections() throws Exception {
        String page = accountPage();

        for (String id : new String[] {"costs", "taxes-section", "payouts-section", "reminders", "backup", "account"}) {
            // Each is a details element, and none starts open.
            assertThat(page).containsPattern("<details class=\"settings-section\" id=\"" + id + "\"");
            assertThat(page).doesNotContainPattern("<details[^>]*id=\"" + id + "\"[^>]* open");
        }
        // The anchors that links already use are still on the cards inside.
        assertThat(page).contains("id=\"goals\"", "id=\"taxes\"", "id=\"payouts\"");
        // No card keeps its small label, because the section title replaces it.
        assertThat(page).doesNotContain("step-label");
    }

    @Test
    void accountPageHasASignOutForm() throws Exception {
        String page = accountPage();

        assertThat(page).containsPattern("<form[^>]*id=\"accountSignOutForm\"[^>]*action=\"/logout\"[^>]*method=\"post\"");
        assertThat(page).containsPattern("(?s)id=\"accountSignOutForm\".*?name=\"_csrf\"");
    }

    @Test
    void theTitleBannerIsGone() throws Exception {
        String page = accountPage();

        assertThat(page).doesNotContain("Export and recovery", "class=\"hero");
        assertThat(page).contains("<h1>Account</h1>", "Angel · angel@example.com", "data-summary=\"account\"");
        assertThat(page).contains("aria-label=\"Back to Home\"", "/js/account-sections.js?v=");
    }

    @Test
    void theAccountHeaderHasNoSubtitleOrAccountButton() throws Exception {
        String page = accountPage();

        assertThat(page).doesNotContain("Account data");
        assertThat(page).contains("<title>Account | FlexBuddy</title>");
        assertThat(page).doesNotContain("account-button");
    }

    @Test
    void theAccountPageOffersFeedbackThatKnowsItsScreen() throws Exception {
        String page = accountPage();

        assertThat(page).containsPattern("<a[^>]*href=\"mailto:flexbuddysupport@gmail.com\"[^>]*data-feedback[^>]*>Send feedback</a>");
        assertThat(page).contains("/js/feedback.js?v=");
    }

    @Test
    void deleteAccount_requiresCsrf() throws Exception {
        mockMvc.perform(post("/account")
                        .with(user("angel@example.com"))
                        .param("_method", "delete")
                        .param("password", "correct-password")
                        .param("confirmation", "delete"))
                .andExpect(status().isForbidden());

        verify(accountService, never()).deleteAccount(any(), any());
    }

    @Test
    void withoutGoogleConfiguredThePagesOfferNoGoogleButton() throws Exception {
        for (String page : new String[] {"/login", "/register"}) {
            mockMvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Continue with Google"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"googleSignIn\""))));
        }
    }

    private AppUser passwordlessUser() {
        AppUser user = new AppUser("Pat", "pat@example.com", "unusable-hash");
        user.setId(5L);
        user.setPasswordSet(false);
        user.setGoogleSubject("sub-1");
        return user;
    }

    @Test
    void deleteAccount_forAnAccountWithoutAPasswordNeedsTheEmailedCode() throws Exception {
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(passwordlessUser()));

        mockMvc.perform(post("/account")
                        .with(user("pat@example.com"))
                        .with(csrf())
                        .param("_method", "delete")
                        .param("confirmation", "delete"))
                .andExpect(status().isOk())
                .andExpect(view().name("account"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter the code we emailed you.")));

        verify(accountService, never()).deleteAccountConfirmed(any());
        verify(accountService, never()).deleteAccount(any(), any());
    }

    @Test
    void deleteAccount_forAnAccountWithoutAPasswordRejectsAWrongCode() throws Exception {
        AppUser pat = passwordlessUser();
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(pat));
        when(emailCodes.check(pat, com.angel.flexbuddy.model.EmailCodePurpose.CONFIRM_DELETE, "000000"))
                .thenReturn(com.angel.flexbuddy.service.EmailCodeService.CheckResult.WRONG);

        mockMvc.perform(post("/account")
                        .with(user("pat@example.com"))
                        .with(csrf())
                        .param("_method", "delete")
                        .param("code", "000000")
                        .param("confirmation", "delete"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("That code isn&#39;t right.")));

        verify(accountService, never()).deleteAccountConfirmed(any());
    }

    @Test
    void deleteAccount_forAnAccountWithoutAPasswordSucceedsWithTheRightCode() throws Exception {
        AppUser pat = passwordlessUser();
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(pat));
        when(emailCodes.check(pat, com.angel.flexbuddy.model.EmailCodePurpose.CONFIRM_DELETE, "493817"))
                .thenReturn(com.angel.flexbuddy.service.EmailCodeService.CheckResult.OK);

        mockMvc.perform(post("/account")
                        .with(user("pat@example.com"))
                        .with(csrf())
                        .param("_method", "delete")
                        .param("code", "493817")
                        .param("confirmation", "delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));

        verify(accountService).deleteAccountConfirmed("pat@example.com");
        verify(accountService, never()).deleteAccount(any(), any());
    }

    @Test
    void deleteAccount_forAnAccountWithAPasswordStillNeedsThePassword() throws Exception {
        when(userRepository.findByEmailIgnoreCase("angel@example.com"))
                .thenReturn(Optional.of(new AppUser("Angel", "angel@example.com", "hash")));

        mockMvc.perform(post("/account")
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .param("_method", "delete")
                        .param("confirmation", "delete"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("deletion", "password"));

        verify(accountService, never()).deleteAccount(any(), any());
    }

    @Test
    void sendingTheDeleteCodeOnlyHappensForAnAccountWithoutAPassword() throws Exception {
        AppUser pat = passwordlessUser();
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(pat));
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(angel));

        mockMvc.perform(post("/account/delete-code").with(user("pat@example.com")).with(csrf()))
                .andExpect(redirectedUrl("/account?deleteCode#account"));
        mockMvc.perform(post("/account/delete-code").with(user("angel@example.com")).with(csrf()))
                .andExpect(redirectedUrl("/account?deleteCode#account"));

        verify(emailCodes).send(org.mockito.ArgumentMatchers.same(pat),
                org.mockito.ArgumentMatchers.eq(com.angel.flexbuddy.model.EmailCodePurpose.CONFIRM_DELETE),
                org.mockito.ArgumentMatchers.anyString());
        verify(emailCodes, never()).send(org.mockito.ArgumentMatchers.same(angel), any(), any());
    }

    @Test
    void googleCanBeUnlinkedOnlyOnceAPasswordExists() throws Exception {
        AppUser pat = passwordlessUser();
        when(userRepository.findByEmailIgnoreCase("pat@example.com")).thenReturn(Optional.of(pat));

        mockMvc.perform(post("/account/google/unlink").with(user("pat@example.com")).with(csrf()))
                .andExpect(redirectedUrl("/account?googleNeedsPassword#account"));
        assertThat(pat.getGoogleSubject()).isEqualTo("sub-1");

        pat.setPasswordSet(true);
        mockMvc.perform(post("/account/google/unlink").with(user("pat@example.com")).with(csrf()))
                .andExpect(redirectedUrl("/account?googleUnlinked#account"));
        assertThat(pat.getGoogleSubject()).isNull();
    }

    @Test
    void deleteAccount_requiresTheExactTypedConfirmation() throws Exception {
        when(userRepository.findByEmailIgnoreCase("angel@example.com"))
                .thenReturn(Optional.of(new AppUser("Angel", "angel@example.com", "hash")));

        mockMvc.perform(post("/account")
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .param("_method", "delete")
                        .param("password", "correct-password")
                        .param("confirmation", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(view().name("account"))
                .andExpect(model().attributeHasFieldErrors("deletion", "confirmation"));

        verify(accountService, never()).deleteAccount(any(), any());
    }
    @Test
    void deleteAccount_rejectsTheWrongPassword() throws Exception {
        when(userRepository.findByEmailIgnoreCase("angel@example.com"))
                .thenReturn(Optional.of(new AppUser("Angel", "angel@example.com", "hash")));
        org.mockito.Mockito.doThrow(new InvalidAccountPasswordException())
                .when(accountService).deleteAccount("angel@example.com", "wrong-password");

        mockMvc.perform(post("/account")
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .param("_method", "delete")
                        .param("password", "wrong-password")
                        .param("confirmation", "delete"))
                .andExpect(status().isOk())
                .andExpect(view().name("account"))
                .andExpect(model().attributeHasFieldErrors("deletion", "password"));
    }

    @Test
    void deleteAccount_removesTheAccountInvalidatesTheSessionAndRedirects() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/account")
                        .session(session)
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .param("_method", "delete")
                        .param("password", "correct-password")
                        .param("confirmation", "delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));

        verify(accountService).deleteAccount("angel@example.com", "correct-password");
        assertThat(session.isInvalid()).isTrue();
    }
    @Test
    void downloadBackup_returnsDatedNoStoreJsonWithoutPasswordData() throws Exception {
        Instant now = Instant.parse("2026-09-11T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(backupService.create("angel@example.com")).thenReturn(new AccountBackupFile(
                "flexbuddy-backup", 1, now, "1.0",
                new BackupAccount("Angel", "angel@example.com", Instant.parse("2026-01-01T00:00:00Z")),
                List.of(), new BackupCounts(0, 0)));

        mockMvc.perform(get("/account/backup").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"flexbuddy-backup-2026-09-11.json\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.format").value("flexbuddy-backup"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))));
    }

    @Test
    void updateReminders_rejectsAnUnknownTimeZone() throws Exception {
        mockMvc.perform(put("/account/reminders").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"Mars/Olympus_Mons\",\"remindBeforeMinutes\":60,\"remindConfirm\":true}"))
                .andExpect(status().isBadRequest());

        verify(settingsService, never()).updateReminders(any(), any());
    }

    @Test
    void updateReminders_rejectsAnUnsupportedLeadTime() throws Exception {
        mockMvc.perform(put("/account/reminders").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"America/Chicago\",\"remindBeforeMinutes\":45,\"remindConfirm\":false}"))
                .andExpect(status().isBadRequest());

        verify(settingsService, never()).updateReminders(any(), any());
    }

    @Test
    void updateTax_acceptsOneToSixtyPercentOrOff() throws Exception {
        for (String body : new String[] {"{\"taxSetAsidePercent\":0}", "{\"taxSetAsidePercent\":61}"}) {
            mockMvc.perform(put("/account/tax").with(user("angel@example.com")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(settingsService, never()).updateTax(any(), any());

        for (String body : new String[] {"{\"taxSetAsidePercent\":25}", "{\"taxSetAsidePercent\":null}"}) {
            mockMvc.perform(put("/account/tax").with(user("angel@example.com")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void updateTax_acceptsTheDueDateReminderSwitch() throws Exception {
        org.mockito.ArgumentCaptor<com.angel.flexbuddy.dto.TaxSettingsRequest> request =
                org.mockito.ArgumentCaptor.forClass(com.angel.flexbuddy.dto.TaxSettingsRequest.class);
        when(settingsService.updateTax(org.mockito.ArgumentMatchers.eq("angel@example.com"), request.capture()))
                .thenReturn(new com.angel.flexbuddy.dto.AccountSettingsResponse(
                        com.angel.flexbuddy.model.VehicleCostMethod.STANDARD_MILEAGE, new java.math.BigDecimal("0.70"),
                        new java.math.BigDecimal("0.70"), 2026, "America/New_York", null, false, false, 45, null, null,
                        com.angel.flexbuddy.model.GoalBasis.GROSS,
                        List.of(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.FRIDAY), 1, new java.math.BigDecimal("25"),
                        null, true));

        mockMvc.perform(put("/account/tax").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taxSetAsidePercent\":25,\"remindTax\":true}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.remindTax").value(true));

        assertThat(request.getValue().remindTax()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void updatePayouts_needsADayAndALagOfUpToTwoWeeks() throws Exception {
        for (String body : new String[] {
                "{\"payoutDays\":[],\"payoutLagDays\":1}",
                "{\"payoutDays\":[\"TUESDAY\"],\"payoutLagDays\":15}",
                "{\"payoutDays\":[\"PAYDAY\"],\"payoutLagDays\":1}"}) {
            mockMvc.perform(put("/account/payouts").with(user("angel@example.com")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(settingsService, never()).updatePayouts(any(), any());

        mockMvc.perform(put("/account/payouts").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payoutDays\":[\"FRIDAY\"],\"payoutLagDays\":0}"))
                .andExpect(status().isOk());
        verify(settingsService).updatePayouts(org.mockito.ArgumentMatchers.eq("angel@example.com"),
                org.mockito.ArgumentMatchers.argThat(request -> request.payoutDays().equals(java.util.Set.of(java.time.DayOfWeek.FRIDAY))
                        && request.payoutLagDays() == 0));
    }

    @Test
    void updateGoals_savesPositiveGoalsAndRejectsTheRest() throws Exception {
        for (String body : new String[] {
                "{\"weeklyGoal\":-5,\"goalBasis\":\"GROSS\"}",
                "{\"weeklyGoal\":0,\"goalBasis\":\"GROSS\"}",
                "{\"weeklyGoal\":600}"}) {
            mockMvc.perform(put("/account/goals").with(user("angel@example.com")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(settingsService, never()).updateGoals(any(), any());

        mockMvc.perform(put("/account/goals").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weeklyGoal\":600,\"monthlyGoal\":null,\"goalBasis\":\"NET\"}"))
                .andExpect(status().isOk());
        verify(settingsService).updateGoals(org.mockito.ArgumentMatchers.eq("angel@example.com"),
                org.mockito.ArgumentMatchers.argThat(request -> request.weeklyGoal().compareTo(new java.math.BigDecimal("600")) == 0
                        && request.monthlyGoal() == null && request.goalBasis() == com.angel.flexbuddy.model.GoalBasis.NET));
    }

    @Test
    void updateReminders_rejectsAForfeitCutoffOutsideTwelveHours() throws Exception {
        mockMvc.perform(put("/account/reminders").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"America/Chicago\",\"remindConfirm\":false,\"forfeitCutoffMinutes\":800}"))
                .andExpect(status().isBadRequest());

        verify(settingsService, never()).updateReminders(any(), any());
    }

    @Test
    void updateReminders_savesValidSettings() throws Exception {
        mockMvc.perform(put("/account/reminders").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"America/Chicago\",\"remindBeforeMinutes\":720,\"remindConfirm\":true}"))
                .andExpect(status().isOk());

        verify(settingsService).updateReminders(org.mockito.ArgumentMatchers.eq("angel@example.com"),
                org.mockito.ArgumentMatchers.argThat(request -> request.timeZone().equals("America/Chicago")
                        && request.remindBeforeMinutes() == 720 && request.remindConfirm()));
    }

    @Test
    void updateReminders_carriesTheMissingMilesChoiceAndLeavesItNullWhenAbsent() throws Exception {
        when(settingsService.updateReminders(any(), any()))
                .thenReturn(new com.angel.flexbuddy.dto.AccountSettingsResponse(
                        com.angel.flexbuddy.model.VehicleCostMethod.STANDARD_MILEAGE, java.math.BigDecimal.ONE,
                        java.math.BigDecimal.ONE, 2025));

        mockMvc.perform(put("/account/reminders").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"America/New_York\",\"remindConfirm\":false,\"askMissingMiles\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.askMissingMiles").value(true));
        mockMvc.perform(put("/account/reminders").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"America/New_York\",\"remindConfirm\":false}"))
                .andExpect(status().isOk());

        verify(settingsService).updateReminders(org.mockito.ArgumentMatchers.eq("angel@example.com"),
                org.mockito.ArgumentMatchers.argThat(request -> Boolean.FALSE.equals(request.askMissingMiles())));
        verify(settingsService).updateReminders(org.mockito.ArgumentMatchers.eq("angel@example.com"),
                org.mockito.ArgumentMatchers.argThat(request -> request.askMissingMiles() == null));
    }

    @Test
    void updateTimeZone_validatesTheZone() throws Exception {
        mockMvc.perform(put("/account/time-zone").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"Nowhere\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/account/time-zone").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"America/Los_Angeles\"}"))
                .andExpect(status().isOk());

        verify(settingsService).updateTimeZone(org.mockito.ArgumentMatchers.eq("angel@example.com"), any());
    }

    @Test
    void regeneratingTheCalendarLinkRequiresCsrf() throws Exception {
        mockMvc.perform(post("/account/calendar-token").with(user("angel@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/account/calendar-token").with(user("angel@example.com")).with(csrf()))
                .andExpect(status().isOk());

        verify(settingsService).regenerateCalendarToken("angel@example.com");
    }
}
