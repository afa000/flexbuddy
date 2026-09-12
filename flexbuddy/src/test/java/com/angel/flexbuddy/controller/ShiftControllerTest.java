package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.exception.InvalidScreenshotException;
import com.angel.flexbuddy.service.ShiftImportService;
import com.angel.flexbuddy.service.ShiftService;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.repository.AppUserRepository;

@WebMvcTest(ShiftController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShiftService shiftService;

    @MockitoBean
    private ShiftImportService shiftImportService;

    @MockitoBean
    private ShiftReportService reportService;

    @MockitoBean
    private AppUserRepository userRepository;

    @Test
    @WithAnonymousUser
    void shiftsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/shifts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void getShiftStatistics_returnsStatisticsJson() throws Exception {
        ShiftStatisticsResponse statistics = new ShiftStatisticsResponse(
                2,
                new BigDecimal("200.00"),
                new BigDecimal("45.50"),
                new BigDecimal("245.50"),
                new BigDecimal("122.75"),
                750,
                new BigDecimal("19.64"),
                new BigDecimal("16.00"),
                new BigDecimal("3.64"),
                new BigDecimal("100.00"),
                new BigDecimal("22.75"),
                new BigDecimal("18.5"),
                375
        );

        when(reportService.statistics(eq("angel@example.com"), any(ShiftFilter.class))).thenReturn(statistics);

        mockMvc.perform(get("/shifts/statistics").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShifts").value(2))
                .andExpect(jsonPath("$.totalBasePay").value(200.00))
                .andExpect(jsonPath("$.totalTips").value(45.50))
                .andExpect(jsonPath("$.totalEarnings").value(245.50))
                .andExpect(jsonPath("$.averagePayPerShift").value(122.75))
                .andExpect(jsonPath("$.totalTimeWorked").value(750))
                .andExpect(jsonPath("$.averageHourlyEarnings").value(19.64));
    }

    @Test
    void getShifts_passesFilterParametersToTheService() throws Exception {
        when(shiftService.getShifts(eq("angel@example.com"), any(ShiftFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/shifts")
                        .with(user("angel@example.com"))
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .param("station", "VEA7")
                        .param("q", "vea")
                        .param("sort", "hourlyRate")
                        .param("dir", "asc"))
                .andExpect(status().isOk());

        verify(shiftService).getShifts(eq("angel@example.com"), eq(ShiftFilter.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "VEA7", "vea", "hourlyRate", "asc")));
    }

    @Test
    void getShifts_returnsBadRequestForInvalidSortOrDateRange() throws Exception {
        mockMvc.perform(get("/shifts").with(user("angel@example.com")).param("sort", "unknown"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/shifts").with(user("angel@example.com"))
                        .param("from", "2026-09-10").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void earningsReport_requiresGroupBy() throws Exception {
        mockMvc.perform(get("/shifts/reports/earnings").with(user("angel@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newReadEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/shifts/stations")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/shifts/reports/earnings").param("groupBy", "month"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void createShift_returnsBadRequestForInvalidInput() throws Exception {
        String invalidRequest = """
                {
                  "station": "",
                  "date": "2026-09-06",
                  "startTime": "09:00",
                  "endTime": "17:00",
                  "basePay": -1.00,
                  "tips": -1.00
                }
                """;

        mockMvc.perform(post("/shifts")
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shiftService);
    }

    @Test
    void updateShift_returnsNotFoundWhenShiftDoesNotExist() throws Exception {
        Long id = 999L;
        String validRequest = """
                {
                  "station": "VEA7",
                  "date": "2026-09-06",
                  "startTime": "09:00",
                  "endTime": "17:00",
                  "basePay": 120.00,
                  "tips": 35.50
                }
                """;

        when(shiftService.updateShift(eq("angel@example.com"), eq(id), any(UpdateShiftRequest.class)))
                .thenThrow(new ShiftNotFoundException(id));

        mockMvc.perform(put("/shifts/{id}", id)
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Shift not found with id: 999"));
    }

    @Test
    void importPreview_returnsUploadedScreenshotMetadata() throws Exception {
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "shift.png",
                "image/png",
                new byte[] {1, 2, 3}
        );
        ShiftImportPreviewResponse response = new ShiftImportPreviewResponse(
                "shift.png",
                "image/png",
                3,
                "Screenshot processed successfully.",
                "Schedule Details",
                2026,
                "VEA7",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(15, 15),
                LocalTime.of(19, 15),
                new BigDecimal("124.00"),
                BigDecimal.ZERO,
                List.of()
        );

        when(shiftImportService.createPreview(any())).thenReturn(response);

        mockMvc.perform(multipart("/shifts/import-preview")
                        .file(screenshot)
                        .with(user("angel@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("shift.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.message").value("Screenshot processed successfully."))
                .andExpect(jsonPath("$.rawText").value("Schedule Details"))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.station").value("VEA7"))
                .andExpect(jsonPath("$.date").value("2026-09-06"))
                .andExpect(jsonPath("$.startTime").value("15:15:00"))
                .andExpect(jsonPath("$.endTime").value("19:15:00"))
                .andExpect(jsonPath("$.basePay").value(124.00))
                .andExpect(jsonPath("$.tips").value(0))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void importPreview_returnsBadRequestForInvalidScreenshot() throws Exception {
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "notes.txt",
                "text/plain",
                "not an image".getBytes()
        );

        when(shiftImportService.createPreview(any()))
                .thenThrow(new InvalidScreenshotException("Unsupported screenshot type."));

        mockMvc.perform(multipart("/shifts/import-preview")
                        .file(screenshot)
                        .with(user("angel@example.com"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unsupported screenshot type."));
    }
}
