package com.angel.flexbuddy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.TaxPaymentResponse;
import com.angel.flexbuddy.dto.TaxQuarterResponse;
import com.angel.flexbuddy.dto.TaxSummaryResponse;
import com.angel.flexbuddy.dto.TaxYearExpense;
import com.angel.flexbuddy.dto.TaxYearReport;
import com.angel.flexbuddy.dto.TaxYearRow;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.TaxService;
import com.angel.flexbuddy.service.UserTimeService;

@WebMvcTest(TaxPageController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class TaxPageControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TaxService taxService;
    @MockitoBean UserTimeService userTime;
    @MockitoBean AppUserRepository userRepository;
    @MockitoBean Clock clock;

    @Test
    @WithAnonymousUser
    void theSummaryPageRequiresSignIn() throws Exception {
        mockMvc.perform(get("/tax/year-summary"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rendersTheYearWithTheEstimateNoticeAndPrintButton() throws Exception {
        when(userRepository.findByEmailIgnoreCase("angel@example.com"))
                .thenReturn(Optional.of(new AppUser("Angel Flores", "angel@example.com", "hash")));
        when(taxService.yearReport("angel@example.com", "Angel Flores", 2026)).thenReturn(report());

        String page = mockMvc.perform(get("/tax/year-summary").param("year", "2026").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Tax year summary · 2026", "$1,234.50", "id=\"printButton\"", "window.print()",
                "not tax advice", "Figures match the CSV download", "Angel Flores", "2026-06", "Est. net",
                "$500.00", "Jun 1", "Aug 31", "Sep 15, 2026");
        // One stubbed expense: its category, the block's station and its amount.
        assertThat(page).containsPattern("(?s)<td[^>]*>Fuel</td>\\s*<td[^>]*>VEA7</td>");
        assertThat(page).contains("$45.20");
        // The fragment that draws each row must not leak into the page as an extra row.
        assertThat(page.split("<th scope=\"row\">2026-06</th>", -1)).hasSize(2);
        assertThat(page).doesNotContain("th:fragment", "th:replace");
    }

    @Test
    void aYearWithNoPercentSaysSoAndShowsADashForTheSetAside() throws Exception {
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.empty());
        when(taxService.yearReport(eq("angel@example.com"), eq("angel@example.com"), eq(2026))).thenReturn(report(null));

        String page = mockMvc.perform(get("/tax/year-summary").param("year", "2026").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("No set-aside percentage chosen.");
        assertThat(page).contains("<td class=\"num\">—</td>");
    }

    @Test
    void defaultsToTheCurrentYearInTheAccountTimeZone() throws Exception {
        when(userTime.today("angel@example.com")).thenReturn(LocalDate.of(2026, 9, 29));
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.empty());
        when(taxService.yearReport(any(), any(), eq(2026))).thenReturn(report());

        mockMvc.perform(get("/tax/year-summary").with(user("angel@example.com")))
                .andExpect(status().isOk());

        verify(taxService).yearReport("angel@example.com", "angel@example.com", 2026);
    }

    @Test
    void aYearOutOfRangeIsABadRequest() throws Exception {
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.empty());
        when(taxService.yearReport(any(), any(), eq(1999))).thenThrow(new InvalidFilterException("year must be between 2000 and 2100."));

        mockMvc.perform(get("/tax/year-summary").param("year", "1999").with(user("angel@example.com")))
                .andExpect(status().isBadRequest());
    }

    private static TaxYearReport report() {
        return report(new BigDecimal("25"));
    }

    private static TaxYearReport report(BigDecimal percent) {
        List<TaxYearRow> months = new java.util.ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            months.add(row(String.format("2026-%02d", month), month == 6 ? "1234.5" : "0.00"));
        }
        TaxYearRow total = row("2026", "1234.5");
        List<TaxQuarterResponse> quarters = List.of(
                new TaxQuarterResponse(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 15),
                        BigDecimal.ZERO, percent == null ? null : BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxQuarterResponse(2, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 15),
                        BigDecimal.ZERO, percent == null ? null : BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxQuarterResponse(3, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 15),
                        new BigDecimal("1234.50"), percent == null ? null : new BigDecimal("308.63"), new BigDecimal("500.00")),
                new TaxQuarterResponse(4, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 15),
                        BigDecimal.ZERO, percent == null ? null : BigDecimal.ZERO, BigDecimal.ZERO));
        TaxSummaryResponse summary = new TaxSummaryResponse(2026, percent, new BigDecimal("1234.50"), null,
                new BigDecimal("500.00"), null, BigDecimal.ZERO, null, null, quarters,
                List.of(new TaxPaymentResponse(1L, 2026, 3, LocalDate.of(2026, 9, 10), new BigDecimal("500.00"), "Direct pay")));
        return new TaxYearReport(2026, LocalDate.of(2026, 9, 29), "Angel Flores", VehicleCostMethod.STANDARD_MILEAGE,
                new BigDecimal("0.70"), months, total, summary,
                List.of(new TaxYearExpense(LocalDate.of(2026, 6, 3), ExpenseCategory.FUEL, new BigDecimal("45.20"), "VEA7", "Shell")));
    }

    private static TaxYearRow row(String label, String gross) {
        BigDecimal zero = new BigDecimal("0.00");
        boolean fuel = label.equals("2026") || label.equals("2026-06");
        return new TaxYearRow(label, gross.equals("0.00") ? 0 : 1, new BigDecimal(gross), zero, new BigDecimal(gross),
                new BigDecimal("0.0"), new BigDecimal("0.70"), zero, fuel ? new BigDecimal("45.20") : zero, zero, zero, zero,
                zero, VehicleCostMethod.STANDARD_MILEAGE, zero, zero, new BigDecimal(gross));
    }
}
