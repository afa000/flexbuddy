package com.angel.flexbuddy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private Clock clock;

    @MockitoBean
    private AppUserRepository userRepository;

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
    void register_createsAccountAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Angel")
                        .param("email", "angel@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        verify(accountService).register(any(RegistrationRequest.class));
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

        verify(accountService, never()).register(any(RegistrationRequest.class));
    }

    @Test
    void register_rejectsAnEmailThatWasTakenBetweenTheCheckAndTheInsert() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"))
                .when(accountService).register(any(RegistrationRequest.class));

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
