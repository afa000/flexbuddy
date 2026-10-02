package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.alert.ErrorAlertService;
import com.angel.flexbuddy.alert.ErrorReport;
import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;

@WebMvcTest(ClientErrorController.class)
@Import(SecurityConfig.class)
class ClientErrorControllerTest {

    private static final String BODY = """
            {"message": "TypeError: x is null", "source": "https://flexbuddy.onrender.com/js/home.js?v=1",
             "line": 12, "column": 5, "screen": "home", "buildId": "20261002.1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ErrorAlertService alerts;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void signedInUser() {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(angel));
    }

    @Test
    void aSignedInReportReturns204AndReachesTheServiceWithTheAccount() throws Exception {
        when(alerts.allowBrowserReport(42L)).thenReturn(true);

        mockMvc.perform(post("/client-errors").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ErrorReport> report = ArgumentCaptor.forClass(ErrorReport.class);
        verify(alerts).report(report.capture());
        org.assertj.core.api.Assertions.assertThat(report.getValue().accountId()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(report.getValue().where()).isEqualTo("/js/home.js:12:5");
    }

    @Test
    void aReportWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/client-errors").with(user("angel@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(alerts, never()).report(any());
    }

    @Test
    void aSignedOutReportIsSentToSignIn() throws Exception {
        mockMvc.perform(post("/client-errors").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(alerts, never()).report(any());
    }

    @Test
    void aMessageOver300CharactersIsRejected() throws Exception {
        String body = "{\"message\": \"" + "x".repeat(301) + "\"}";

        mockMvc.perform(post("/client-errors").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aThrottledReportStillReturns204AndSendsNothing() throws Exception {
        when(alerts.allowBrowserReport(42L)).thenReturn(false);

        mockMvc.perform(post("/client-errors").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent());

        verify(alerts, never()).report(any());
    }
}
