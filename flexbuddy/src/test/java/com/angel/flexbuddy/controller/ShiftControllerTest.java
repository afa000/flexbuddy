package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.service.ShiftService;

@WebMvcTest(ShiftController.class)
@Import(GlobalExceptionHandler.class)
class ShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShiftService shiftService;

    @Test
    void getShiftStatistics_returnsStatisticsJson() throws Exception {
        ShiftStatisticsResponse statistics = new ShiftStatisticsResponse(
                2,
                new BigDecimal("200.00"),
                new BigDecimal("45.50"),
                new BigDecimal("245.50"),
                new BigDecimal("122.75"),
                750
        );

        when(shiftService.getShiftStatistics()).thenReturn(statistics);

        mockMvc.perform(get("/shifts/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShifts").value(2))
                .andExpect(jsonPath("$.totalBasePay").value(200.00))
                .andExpect(jsonPath("$.totalTips").value(45.50))
                .andExpect(jsonPath("$.totalEarnings").value(245.50))
                .andExpect(jsonPath("$.averagePayPerShift").value(122.75))
                .andExpect(jsonPath("$.totalTimeWorked").value(750));
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

        when(shiftService.updateShift(eq(id), any(UpdateShiftRequest.class)))
                .thenThrow(new ShiftNotFoundException(id));

        mockMvc.perform(put("/shifts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Shift not found with id: 999"));
    }
}
