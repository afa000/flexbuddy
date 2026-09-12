package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.AccountService;
import com.angel.flexbuddy.service.AccountBackupService;
import com.angel.flexbuddy.service.AccountRestoreService;
import com.angel.flexbuddy.service.AccountSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
    void errorPageIsReachableWithoutASession() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(redirectedUrl(null))
                .andExpect(status().is(org.hamcrest.Matchers.not(org.hamcrest.Matchers.is(302))));
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
}
