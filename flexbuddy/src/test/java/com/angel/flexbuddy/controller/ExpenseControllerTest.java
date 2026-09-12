package com.angel.flexbuddy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.ExpenseRequest;
import com.angel.flexbuddy.dto.ExpenseResponse;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.ExpenseCsvWriter;
import com.angel.flexbuddy.service.ExpenseService;

@WebMvcTest(ExpenseController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ExpenseControllerTest {
    private static final String EMAIL = "angel@example.com";

    @Autowired MockMvc mockMvc;
    @MockitoBean ExpenseService expenseService;
    @MockitoBean ExpenseCsvWriter csvWriter;
    @MockitoBean Clock clock;
    @MockitoBean AppUserRepository userRepository;

    @Test
    void anonymousRequestsAreSentToLogin() throws Exception {
        mockMvc.perform(get("/expenses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void createValidatesAndPassesTheSignedInOwnerToTheService() throws Exception {
        when(expenseService.create(eq(EMAIL), any(ExpenseRequest.class))).thenReturn(new ExpenseResponse(
                20L, LocalDate.of(2026, 9, 12), ExpenseCategory.TOLL, new BigDecimal("6.25"),
                "Bridge", 8L, "VEA7", null, null, null));

        mockMvc.perform(post("/expenses").with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-09-12","category":"TOLL","amount":6.25,
                                 "note":"Bridge","shiftId":8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.station").value("VEA7"));

        ArgumentCaptor<ExpenseRequest> request = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).create(eq(EMAIL), request.capture());
        assertThat(request.getValue().shiftId()).isEqualTo(8L);
        assertThat(request.getValue().amount()).isEqualByComparingTo("6.25");
    }

    @Test
    void createRejectsInvalidAmountsBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/expenses").with(user(EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-09-12","category":"FUEL","amount":0}
                                """))
                .andExpect(status().isBadRequest());

        verify(expenseService, never()).create(any(), any());
    }

    @Test
    void listParsesDateCategoryAndShiftFilters() throws Exception {
        when(expenseService.getExpenses(eq(EMAIL), any(ExpenseFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/expenses").with(user(EMAIL))
                        .param("from", "2026-09-01").param("to", "2026-09-30")
                        .param("category", "fuel").param("shiftId", "8"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExpenseFilter> filter = ArgumentCaptor.forClass(ExpenseFilter.class);
        verify(expenseService).getExpenses(eq(EMAIL), filter.capture());
        assertThat(filter.getValue().category()).isEqualTo(ExpenseCategory.FUEL);
        assertThat(filter.getValue().shiftId()).isEqualTo(8L);
        assertThat(filter.getValue().from()).isEqualTo(LocalDate.of(2026, 9, 1));
    }
}
