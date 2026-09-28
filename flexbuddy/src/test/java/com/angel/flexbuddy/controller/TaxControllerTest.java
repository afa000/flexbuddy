package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
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
import org.springframework.test.web.servlet.MvcResult;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.exception.TaxPaymentNotFoundException;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.TaxService;
import com.angel.flexbuddy.service.UserTimeService;

@WebMvcTest(TaxController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class TaxControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TaxService taxService;
    @MockitoBean UserTimeService userTime;
    @MockitoBean AppUserRepository userRepository;
    @MockitoBean Clock clock;

    @Test
    @WithAnonymousUser
    void taxFiguresRequireSignIn() throws Exception {
        mockMvc.perform(get("/tax/summary"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void theYearCsvDownloadsForTheCurrentYearByDefault() throws Exception {
        when(userTime.today("angel@example.com")).thenReturn(LocalDate.of(2026, 9, 10));
        doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(2);
            output.write("month,net\r\n".getBytes());
            return null;
        }).when(taxService).writeYearCsv(eq("angel@example.com"), eq(2026), any(OutputStream.class));

        MvcResult pending = mockMvc.perform(get("/tax/summary.csv").with(user("angel@example.com")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"flexbuddy-tax-summary-2026.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string("month,net\r\n"));
    }

    @Test
    void aPaymentNeedsAYearADateAndAPositiveAmount() throws Exception {
        for (String body : new String[] {
                "{\"taxYear\":2026,\"paidOn\":\"2026-09-14\",\"amount\":0}",
                "{\"taxYear\":2026,\"paidOn\":\"2026-09-14\",\"amount\":-5}",
                "{\"taxYear\":2026,\"amount\":500}",
                "{\"paidOn\":\"2026-09-14\",\"amount\":500}",
                "{\"taxYear\":2026,\"quarter\":5,\"paidOn\":\"2026-09-14\",\"amount\":500}"}) {
            mockMvc.perform(post("/tax/payments").with(user("angel@example.com")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(taxService, never()).addPayment(any(), any());

        mockMvc.perform(post("/tax/payments").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxYear\":2026,\"quarter\":3,\"paidOn\":\"2026-09-14\",\"amount\":500}"))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAnotherDriversPaymentIsNotFound() throws Exception {
        doThrow(new TaxPaymentNotFoundException(9L)).when(taxService).deletePayment("angel@example.com", 9L);

        mockMvc.perform(delete("/tax/payments/9").with(user("angel@example.com")).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/tax/payments/10").with(user("angel@example.com")).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
