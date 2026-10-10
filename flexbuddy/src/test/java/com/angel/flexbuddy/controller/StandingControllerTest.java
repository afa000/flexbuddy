package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.dto.StandingEntryRequest;
import com.angel.flexbuddy.dto.StandingEntryResponse;
import com.angel.flexbuddy.dto.StandingEventKind;
import com.angel.flexbuddy.dto.StandingEventResponse;
import com.angel.flexbuddy.dto.StandingResponse;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.exception.InvalidStandingException;
import com.angel.flexbuddy.model.StandingLevel;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.StandingService;

@WebMvcTest(StandingController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class StandingControllerTest {

    private static final LocalDate SEP_10 = LocalDate.of(2026, 9, 10);

    @Autowired MockMvc mockMvc;
    @MockitoBean StandingService standingService;
    @MockitoBean AppUserRepository userRepository;
    @MockitoBean Clock clock;

    @Test
    @WithAnonymousUser
    void standingRequiresSignIn() throws Exception {
        mockMvc.perform(get("/standing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void returnsTheWindowAsJson() throws Exception {
        StandingEntryResponse great = new StandingEntryResponse(LocalDate.of(2026, 6, 1), StandingLevel.GREAT, null);
        StandingEntryResponse fair = new StandingEntryResponse(SEP_10, StandingLevel.FAIR, "After late forfeit");
        when(standingService.window("angel@example.com", 90)).thenReturn(new StandingResponse(
                LocalDate.of(2026, 7, 2), LocalDate.of(2026, 9, 29), fair, List.of(great, fair),
                List.of(new StandingEventResponse(LocalDate.of(2026, 9, 8), LocalTime.of(9, 0), "VEA7", StandingEventKind.LATE_FORFEIT))));

        mockMvc.perform(get("/standing").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.level").value("FAIR"))
                .andExpect(jsonPath("$.entries[0].recordedOn").value("2026-06-01"))
                .andExpect(jsonPath("$.entries[1].note").value("After late forfeit"))
                .andExpect(jsonPath("$.events[0].kind").value("LATE_FORFEIT"))
                .andExpect(jsonPath("$.events[0].station").value("VEA7"));
    }

    @Test
    void anotherWindowLengthCanBeAsked() throws Exception {
        when(standingService.window("angel@example.com", 30)).thenReturn(new StandingResponse(
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 29), null, List.of(), List.of()));

        mockMvc.perform(get("/standing").param("days", "30").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").doesNotExist());
    }

    @Test
    void logsAStandingForADay() throws Exception {
        mockMvc.perform(put("/standing/2026-09-10").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"FAIR\",\"note\":\"x\"}"))
                .andExpect(status().isNoContent());

        verify(standingService).log("angel@example.com", SEP_10, new StandingEntryRequest(StandingLevel.FAIR, "x"));
    }

    @Test
    void aMissingOrUnknownLevelIsRejected() throws Exception {
        mockMvc.perform(put("/standing/2026-09-10").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/standing/2026-09-10").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"GOOD\"}"))
                .andExpect(status().isBadRequest());

        verify(standingService, never()).log(any(), any(), any());
    }

    @Test
    void aNoteOver255CharactersIsRejected() throws Exception {
        mockMvc.perform(put("/standing/2026-09-10").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"GREAT\",\"note\":\"" + "n".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(standingService, never()).log(any(), any(), any());
    }

    @Test
    void aFutureDayExplainsWhy() throws Exception {
        doThrow(new InvalidStandingException("error.standing.notFuture"))
                .when(standingService).log(eq("angel@example.com"), eq(LocalDate.of(2026, 9, 30)), any());

        mockMvc.perform(put("/standing/2026-09-30").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"GREAT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Standing can be logged for today or an earlier day."));
    }

    @Test
    void aPathThatIsNotADateIsRejected() throws Exception {
        mockMvc.perform(put("/standing/soon").with(user("angel@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"GREAT\"}"))
                .andExpect(status().isBadRequest());

        verify(standingService, never()).log(any(), any(), any());
    }

    @Test
    void removesADaysStanding() throws Exception {
        mockMvc.perform(delete("/standing/2026-09-10").with(user("angel@example.com")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(standingService).remove("angel@example.com", SEP_10);
    }
}
