package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.PayoutDepositRequest;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.exception.InvalidPayoutException;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.PayoutService;

@WebMvcTest(PayoutController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class PayoutControllerTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 11);

    @Autowired MockMvc mockMvc;
    @MockitoBean PayoutService payoutService;
    @MockitoBean AppUserRepository userRepository;
    @MockitoBean Clock clock;

    @Test
    @WithAnonymousUser
    void recordingAPayoutRequiresSignIn() throws Exception {
        mockMvc.perform(put("/payouts/2026-09-11").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":84.00}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void recordsWhatLandedForAPayout() throws Exception {
        mockMvc.perform(put("/payouts/2026-09-11").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":84.00,\"note\":\"Chase\"}"))
                .andExpect(status().isNoContent());

        verify(payoutService).record("angel@example.com", FRIDAY, new PayoutDepositRequest(new BigDecimal("84.00"), "Chase"));
    }

    @Test
    void aZeroPayoutCanBeRecordedButNotANegativeOrMissingOne() throws Exception {
        mockMvc.perform(put("/payouts/2026-09-11").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/payouts/2026-09-11").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/payouts/2026-09-11").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(payoutService).record(eq("angel@example.com"), eq(FRIDAY), any());
    }

    @Test
    void aPayoutTheScheduleDoesNotAllowExplainsWhy() throws Exception {
        doThrow(new InvalidPayoutException("That date is not one of your payout days."))
                .when(payoutService).record(eq("angel@example.com"), eq(LocalDate.of(2026, 9, 10)), any());

        mockMvc.perform(put("/payouts/2026-09-10").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":84.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("That date is not one of your payout days."));
    }

    @Test
    void aDateThatIsNotADateIsRejected() throws Exception {
        mockMvc.perform(put("/payouts/next-friday").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":84.00}"))
                .andExpect(status().isBadRequest());

        verify(payoutService, never()).record(any(), any(), any());
    }

    @Test
    void removesARecordedPayout() throws Exception {
        mockMvc.perform(delete("/payouts/2026-09-11").with(user("angel@example.com")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(payoutService).remove("angel@example.com", FRIDAY);
    }
}
