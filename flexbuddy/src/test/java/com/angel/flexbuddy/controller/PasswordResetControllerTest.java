package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.exception.InvalidResetTokenException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.PasswordResetService;

@WebMvcTest(PasswordResetController.class)
@Import(SecurityConfig.class)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService resetService;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private Clock clock;

    private AppUser angel() {
        return new AppUser("Angel", "angel@example.com", "hash");
    }

    @Test
    void thePagesArePublic() throws Exception {
        when(resetService.checkToken("good-token")).thenReturn(Optional.of(angel()));

        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"));
        mockMvc.perform(get("/reset-password").param("token", "good-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"));
    }

    @Test
    void theConfirmationReplacesTheForm() throws Exception {
        mockMvc.perform(get("/forgot-password").param("sent", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("If an account uses that email")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Send reset link"))));
    }

    @Test
    void requestingAlwaysRedirectsToTheSamePage() throws Exception {
        for (String email : new String[] {"angel@example.com", "nobody@example.com"}) {
            mockMvc.perform(post("/forgot-password").with(csrf()).param("email", email))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/forgot-password?sent"));
            // MockMvc's default connection address.
            verify(resetService).requestReset(email, "127.0.0.1");
        }
    }

    @Test
    void aBadlyFormedEmailShowsTheFieldError() throws Exception {
        mockMvc.perform(post("/forgot-password").with(csrf()).param("email", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attributeHasFieldErrors("forgot", "email"));

        verify(resetService, never()).requestReset(anyString(), anyString());
    }

    @Test
    void anInvalidLinkShowsTheInvalidPageWithPrivateHeaders() throws Exception {
        when(resetService.checkToken("bad-token")).thenReturn(Optional.empty());
        when(resetService.checkToken("good-token")).thenReturn(Optional.of(angel()));

        mockMvc.perform(get("/reset-password").param("token", "bad-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password-invalid"))
                .andExpect(content().string(Matchers.containsString("This link has expired or was already used")))
                .andExpect(content().string(Matchers.containsString("/forgot-password")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mockMvc.perform(get("/reset-password"))
                .andExpect(view().name("reset-password-invalid"));
        mockMvc.perform(get("/reset-password").param("token", "good-token"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void mismatchedOrShortPasswordsShowFieldErrorsAndKeepTheToken() throws Exception {
        when(resetService.checkToken("good-token")).thenReturn(Optional.of(angel()));

        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("token", "good-token").param("password", "long-enough-1").param("confirmPassword", "different-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(model().attributeHasFieldErrors("reset", "matching"))
                .andExpect(content().string(Matchers.containsString("value=\"good-token\"")))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("token", "good-token").param("password", "short").param("confirmPassword", "short"))
                .andExpect(view().name("reset-password"))
                .andExpect(model().attributeHasFieldErrors("reset", "password"));

        verify(resetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void aSuccessfulResetRedirectsToSignInWithReset() throws Exception {
        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("token", "good-token").param("password", "new-password-1").param("confirmPassword", "new-password-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset"));

        verify(resetService).resetPassword("good-token", "new-password-1");
    }

    @Test
    void aLinkThatExpiredWhileTheFormWasOpenShowsTheInvalidPage() throws Exception {
        doThrow(new InvalidResetTokenException()).when(resetService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("token", "stale-token").param("password", "new-password-1").param("confirmPassword", "new-password-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password-invalid"));
    }

    @Test
    void postsWithoutCsrfAreRejected() throws Exception {
        mockMvc.perform(post("/forgot-password").param("email", "angel@example.com"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/reset-password")
                        .param("token", "good-token").param("password", "new-password-1").param("confirmPassword", "new-password-1"))
                .andExpect(status().isForbidden());

        verify(resetService, never()).requestReset(any(), any());
        verify(resetService, never()).resetPassword(any(), any());
    }
}
