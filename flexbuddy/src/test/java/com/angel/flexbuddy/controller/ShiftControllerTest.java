package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.BlockEvaluationRequest;
import com.angel.flexbuddy.dto.BlockEvaluationResponse;
import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftCandidate;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftStatusRequest;
import com.angel.flexbuddy.exception.InvalidShiftException;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.exception.InvalidScreenshotException;
import com.angel.flexbuddy.service.BlockEvaluator;
import com.angel.flexbuddy.service.ShiftImportService;
import com.angel.flexbuddy.service.ShiftService;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.service.ShiftCsvWriter;
import com.angel.flexbuddy.service.ExpenseService;
import com.angel.flexbuddy.service.OcrLine;
import com.angel.flexbuddy.service.ParsedField;
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
    private ShiftCsvWriter csvWriter;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private BlockEvaluator blockEvaluator;

    @Test
    @WithAnonymousUser
    void shiftsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/shifts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithAnonymousUser
    void evaluateRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/shifts/evaluate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station\":\"VEA7\",\"hours\":4,\"offeredPay\":84}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(blockEvaluator);
    }

    @Test
    void evaluate_returnsTheEstimateForTheSignedInDriver() throws Exception {
        when(blockEvaluator.evaluate(eq("angel@example.com"), any(BlockEvaluationRequest.class))).thenReturn(
                new BlockEvaluationResponse(BlockEvaluationResponse.Basis.STATION_90_DAYS, 12, "VEA7",
                        new BigDecimal("21.00"), new BigDecimal("22.50"), new BigDecimal("6.00"), new BigDecimal("22.0"),
                        new BigDecimal("15.40"), new BigDecimal("0.33"), new BigDecimal("74.27"), new BigDecimal("18.57"),
                        new BigDecimal("18.57"), new BigDecimal("22.50"), BlockEvaluationResponse.Verdict.ABOUT_USUAL,
                        new BigDecimal("0.0"), new BigDecimal("18.00"), new BigDecimal("12.00")));

        mockMvc.perform(post("/shifts/evaluate").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station\":\"VEA7\",\"hours\":4,\"offeredPay\":84.00,\"expectedTips\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("STATION_90_DAYS"))
                .andExpect(jsonPath("$.sampleSize").value(12))
                .andExpect(jsonPath("$.estimatedNetHourly").value(18.57))
                .andExpect(jsonPath("$.verdict").value("ABOUT_USUAL"));

        verify(blockEvaluator).evaluate("angel@example.com", new BlockEvaluationRequest(
                "VEA7", new BigDecimal("4"), new BigDecimal("84.00"), new BigDecimal("6")));
    }

    @Test
    void evaluate_rejectsIncompleteOrOutOfRangeOffers() throws Exception {
        for (String body : List.of(
                "{\"station\":\" \",\"hours\":4,\"offeredPay\":84}",
                "{\"station\":\"VEA7\",\"hours\":0.25,\"offeredPay\":84}",
                "{\"station\":\"VEA7\",\"hours\":12.5,\"offeredPay\":84}",
                "{\"station\":\"VEA7\",\"offeredPay\":84}",
                "{\"station\":\"VEA7\",\"hours\":4,\"offeredPay\":0}",
                "{\"station\":\"VEA7\",\"hours\":4,\"offeredPay\":84,\"expectedTips\":-1}")) {
            mockMvc.perform(post("/shifts/evaluate").with(user("angel@example.com")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(blockEvaluator);
    }

    @Test
    void exportCsv_usesActiveFiltersAndReturnsRangeFilename() throws Exception {
        when(shiftService.getShifts(eq("angel@example.com"), any(ShiftFilter.class))).thenReturn(List.of());
        doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(1);
            output.write("id,date\r\n".getBytes());
            return null;
        }).when(csvWriter).write(any(), any(OutputStream.class));

        MvcResult pending = mockMvc.perform(get("/shifts/export.csv")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .param("station", "VEA7")
                        .param("q", "north")
                        .param("sort", "date")
                        .param("dir", "desc")
                        .with(user("angel@example.com")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"flexbuddy-shifts-2026-09-01_to_2026-09-30.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string("id,date\r\n"));

        verify(shiftService).getShifts(eq("angel@example.com"), org.mockito.ArgumentMatchers.argThat(filter ->
                filter.from().equals(LocalDate.of(2026, 9, 1))
                        && filter.to().equals(LocalDate.of(2026, 9, 30))
                        && filter.station().equals("VEA7")
                        && filter.query().equals("north")
                        && filter.statuses().equals(ShiftStatus.EARNINGS)));
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
                .andExpect(jsonPath("$.averageHourlyEarnings").value(19.64))
                .andExpect(jsonPath("$.rollingSevenDayMinutes").value(0));
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
    void createShift_returnsBadRequestWhenStationExceedsDatabaseLimit() throws Exception {
        String invalidRequest = """
                {
                  "station": "%s",
                  "date": "2026-09-06",
                  "startTime": "09:00",
                  "endTime": "17:00",
                  "basePay": 120.00,
                  "tips": 0.00
                }
                """.formatted("A".repeat(256));

        mockMvc.perform(post("/shifts")
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shiftService);
    }

    @Test
    void updateShift_returnsBadRequestWhenStationExceedsDatabaseLimit() throws Exception {
        String invalidRequest = """
                {
                  "station": "%s",
                  "date": "2026-09-06",
                  "startTime": "09:00",
                  "endTime": "17:00",
                  "basePay": 120.00,
                  "tips": 0.00
                }
                """.formatted("A".repeat(256));

        mockMvc.perform(put("/shifts/{id}", 1L)
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

        when(shiftService.updateShift(eq("angel@example.com"), eq(id), any(UpdateShiftRequest.class), org.mockito.ArgumentMatchers.isNull()))
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
                "Found 1 shift. Review the imported values before saving.",
                "Schedule Details",
                2026,
                92,
                List.of(new OcrLine("Schedule Details", 92, 0, 0, 100, 20, 0)),
                List.of(new ShiftCandidate(
                        0,
                        ParsedField.defaulted("VEA7"),
                        ParsedField.defaulted(LocalDate.of(2026, 9, 6)),
                        ParsedField.defaulted(LocalTime.of(15, 15)),
                        ParsedField.defaulted(LocalTime.of(19, 15)),
                        ParsedField.defaulted(new BigDecimal("124.00")),
                        ParsedField.defaulted(BigDecimal.ZERO),
                        List.of(),
                        List.of(),
                        List.of(0, 0)
                ))
        );

        when(shiftImportService.createPreview(any(), any())).thenReturn(response);

        mockMvc.perform(multipart("/shifts/import-preview")
                        .file(screenshot)
                        .with(user("angel@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("shift.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.message").value("Found 1 shift. Review the imported values before saving."))
                .andExpect(jsonPath("$.rawText").value("Schedule Details"))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.meanConfidence").value(92))
                .andExpect(jsonPath("$.shifts[0].station.value").value("VEA7"))
                .andExpect(jsonPath("$.shifts[0].date.value").value("2026-09-06"))
                .andExpect(jsonPath("$.shifts[0].startTime.value").value("15:15:00"))
                .andExpect(jsonPath("$.shifts[0].endTime.value").value("19:15:00"))
                .andExpect(jsonPath("$.shifts[0].basePay.value").value(124.00))
                .andExpect(jsonPath("$.shifts[0].tips.value").value(0))
                .andExpect(jsonPath("$.shifts[0].warnings").isEmpty());
    }

    @Test
    void importPreview_returnsBadRequestForInvalidScreenshot() throws Exception {
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "notes.txt",
                "text/plain",
                "not an image".getBytes()
        );

        when(shiftImportService.createPreview(any(), any()))
                .thenThrow(new InvalidScreenshotException("Unsupported screenshot type."));

        mockMvc.perform(multipart("/shifts/import-preview")
                        .file(screenshot)
                        .with(user("angel@example.com"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unsupported screenshot type."));
    }

    @Test
    void changeStatus_sendsTheNewStatusAndPayToTheService() throws Exception {
        ShiftResponse cancelled = new ShiftResponse();
        cancelled.setId(5L);
        cancelled.setStatus(ShiftStatus.CANCELLED);
        when(shiftService.changeStatus(eq("angel@example.com"), eq(5L), any(ShiftStatusRequest.class), org.mockito.ArgumentMatchers.isNull())).thenReturn(cancelled);

        mockMvc.perform(patch("/shifts/{id}/status", 5L)
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"basePay\":18.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(shiftService).changeStatus(eq("angel@example.com"), eq(5L), org.mockito.ArgumentMatchers.argThat(request ->
                request.status() == ShiftStatus.CANCELLED && request.basePay().compareTo(new BigDecimal("18.00")) == 0),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void changeStatus_returnsTheRuleThatWasBroken() throws Exception {
        when(shiftService.changeStatus(eq("angel@example.com"), eq(5L), any(ShiftStatusRequest.class), org.mockito.ArgumentMatchers.isNull()))
                .thenThrow(new InvalidShiftException("A completed shift cannot be moved back to scheduled. Delete it and add the block again."));

        mockMvc.perform(patch("/shifts/{id}/status", 5L)
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("A completed shift cannot be moved back to scheduled. Delete it and add the block again."));
    }

    @Test
    void changeStatus_requiresAStatus() throws Exception {
        mockMvc.perform(patch("/shifts/{id}/status", 5L)
                        .with(user("angel@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shiftService);
    }

    @Test
    void getShifts_acceptsACommaSeparatedStatusList() throws Exception {
        when(shiftService.getShifts(eq("angel@example.com"), any(ShiftFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/shifts").with(user("angel@example.com")).param("status", "scheduled,completed"))
                .andExpect(status().isOk());

        verify(shiftService).getShifts(eq("angel@example.com"), org.mockito.ArgumentMatchers.argThat(filter ->
                filter.statuses().equals(Set.of(ShiftStatus.SCHEDULED, ShiftStatus.COMPLETED))));
    }

    @Test
    void startShift_stampsTheBlockForTheSignedInDriver() throws Exception {
        ShiftResponse started = new ShiftResponse();
        started.setId(7L);
        started.setStatus(ShiftStatus.SCHEDULED);
        started.setDetails(new com.angel.flexbuddy.dto.BlockDetailsResponse(LocalTime.of(15, 3), null, null, null, null, null, null, null, null, null, null, null));
        when(shiftService.startShift("angel@example.com", 7L)).thenReturn(started);

        mockMvc.perform(post("/shifts/7/start").with(user("angel@example.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.actualStart").value("15:03:00"));
    }

    @Test
    void startShift_returnsNotFoundForAnotherDriversBlock() throws Exception {
        when(shiftService.startShift("angel@example.com", 99L)).thenThrow(new ShiftNotFoundException(99L));

        mockMvc.perform(post("/shifts/99/start").with(user("angel@example.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void latestOdometer_returnsTheReadingOrNoContent() throws Exception {
        when(shiftService.latestOdometer("angel@example.com", java.time.LocalDateTime.of(2026, 9, 13, 15, 15)))
                .thenReturn(java.util.Optional.of(new com.angel.flexbuddy.dto.OdometerReadingResponse(
                        new BigDecimal("45210.4"), LocalDate.of(2026, 9, 6), "VEA7")));
        when(shiftService.latestOdometer("angel@example.com", null)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/shifts/odometer/latest").param("before", "2026-09-13T15:15")
                        .with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reading").value(45210.4))
                .andExpect(jsonPath("$.station").value("VEA7"));
        mockMvc.perform(get("/shifts/odometer/latest").with(user("angel@example.com")))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingMiles_usesAWeekByDefaultAndRejectsABadWindow() throws Exception {
        when(shiftService.missingMiles("angel@example.com", 7)).thenReturn(List.of());
        when(shiftService.missingMiles("angel@example.com", 40))
                .thenThrow(new com.angel.flexbuddy.exception.InvalidFilterException("days must be between 1 and 31."));

        mockMvc.perform(get("/shifts/missing-miles").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/shifts/missing-miles").param("days", "40").with(user("angel@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getShift_returnsOneOfTheDriversShifts() throws Exception {
        ShiftResponse shift = new ShiftResponse();
        shift.setId(7L);
        shift.setStation("VEA7");
        when(shiftService.getShift("angel@example.com", 7L)).thenReturn(shift);
        when(shiftService.getShift("angel@example.com", 99L)).thenThrow(new ShiftNotFoundException(99L));

        mockMvc.perform(get("/shifts/7").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.station").value("VEA7"));
        mockMvc.perform(get("/shifts/99").with(user("angel@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void goals_returnsWeekAndMonthProgress() throws Exception {
        when(reportService.goals("angel@example.com")).thenReturn(new com.angel.flexbuddy.dto.GoalsResponse(
                com.angel.flexbuddy.model.GoalBasis.GROSS,
                new com.angel.flexbuddy.dto.GoalProgress(new BigDecimal("600.00"), new BigDecimal("412.50"),
                        new BigDecimal("84.00"), new BigDecimal("103.50"), new BigDecimal("68.8"), true, 2,
                        new BigDecimal("78.40"), 240, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13)),
                null));

        mockMvc.perform(get("/shifts/goals").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("GROSS"))
                .andExpect(jsonPath("$.week.percent").value(68.8))
                .andExpect(jsonPath("$.week.blocksToGo").value(2))
                .andExpect(jsonPath("$.week.periodStart").value("2026-09-07"))
                .andExpect(jsonPath("$.month").doesNotExist());
    }

    @Test
    void payPeriods_returnsTheNextPayoutAndRecentPeriods() throws Exception {
        com.angel.flexbuddy.dto.PayPeriodResponse current = new com.angel.flexbuddy.dto.PayPeriodResponse(
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14), 2,
                new BigDecimal("108.00"), 1, new BigDecimal("84.00"), null, null, null,
                com.angel.flexbuddy.dto.PayoutStatus.UPCOMING);
        when(reportService.payPeriods("angel@example.com", 2))
                .thenReturn(new com.angel.flexbuddy.dto.PayPeriodsResponse(current, List.of(current)));

        mockMvc.perform(get("/shifts/pay-periods").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextPayout.payoutDate").value("2026-09-15"))
                .andExpect(jsonPath("$.periods[0].from").value("2026-09-11"))
                .andExpect(jsonPath("$.periods[0].earned").value(108.00));
    }

    @Test
    void heatmap_rejectsAnUnknownMetric() throws Exception {
        mockMvc.perform(get("/shifts/reports/heatmap").param("metric", "luck").with(user("angel@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void heatmap_requiresSignIn() throws Exception {
        mockMvc.perform(get("/shifts/reports/heatmap"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private static final String CREATE_JSON =
            "{\"station\":\"VEA7\",\"date\":\"2026-09-12\",\"startTime\":\"09:00\",\"endTime\":\"13:00\",\"basePay\":80,\"tips\":10}";

    @Test
    void anIdempotencyKeyThatIsNotAUuidIsRejected() throws Exception {
        mockMvc.perform(post("/shifts").with(user("angel@example.com")).with(csrf())
                        .header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Idempotency-Key must be a UUID."));

        verifyNoInteractions(shiftService);
    }

    @Test
    void theKeyIsPassedLowerCased() throws Exception {
        when(shiftService.createShift(eq("angel@example.com"), any(), any())).thenReturn(new ShiftResponse());

        mockMvc.perform(post("/shifts").with(user("angel@example.com")).with(csrf())
                        .header("Idempotency-Key", "AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isOk());

        verify(shiftService).createShift(eq("angel@example.com"), any(), eq("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
    }

    @Test
    void anEditPassesItsKeyToTheService() throws Exception {
        when(shiftService.updateShift(eq("angel@example.com"), eq(5L), any(UpdateShiftRequest.class), any()))
                .thenReturn(new ShiftResponse());

        mockMvc.perform(put("/shifts/{id}", 5L).with(user("angel@example.com")).with(csrf())
                        .header("Idempotency-Key", "11111111-1111-4111-8111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isOk());

        verify(shiftService).updateShift(eq("angel@example.com"), eq(5L), any(UpdateShiftRequest.class),
                eq("11111111-1111-4111-8111-111111111111"));
    }

    @Test
    void aConflictIs409WithTheCurrentShift() throws Exception {
        ShiftResponse current = new ShiftResponse();
        current.setId(5L);
        current.setUpdatedAt(java.time.Instant.parse("2026-09-11T12:00:00Z"));
        when(shiftService.changeStatus(eq("angel@example.com"), eq(5L), any(ShiftStatusRequest.class), any()))
                .thenThrow(new com.angel.flexbuddy.exception.ShiftConflictException(current));

        mockMvc.perform(patch("/shifts/{id}/status", 5L).with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"miles\":10,\"expectedUpdatedAt\":\"2026-09-10T00:00:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.current.id").value(5))
                .andExpect(jsonPath("$.current.updatedAt").exists());
    }

    @Test
    void aDuplicateInsertIs409Duplicate() throws Exception {
        when(shiftService.createShift(eq("angel@example.com"), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("could not execute statement",
                        new RuntimeException("Unique index or primary key violation: UK_SHIFT_OWNER_CREATE_REQUEST")));

        mockMvc.perform(post("/shifts").with(user("angel@example.com")).with(csrf())
                        .header("Idempotency-Key", "11111111-1111-4111-8111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void anyOtherConstraintFailureStaysAGenericServerError() throws Exception {
        when(shiftService.createShift(eq("angel@example.com"), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("could not execute statement",
                        new RuntimeException("violates foreign key constraint fk_shift_owner")));

        mockMvc.perform(post("/shifts").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("The change could not be saved."));
    }
}
