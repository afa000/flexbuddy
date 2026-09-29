package com.angel.flexbuddy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;

@WebMvcTest(CsrfController.class)
@Import(SecurityConfig.class)
class CsrfControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AppUserRepository userRepository;
    @MockitoBean Clock clock;

    @Test
    void returnsAFreshTokenAndTheAccountId() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(angel));

        mockMvc.perform(get("/csrf").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.accountId").value(42))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @WithAnonymousUser
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/csrf"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
