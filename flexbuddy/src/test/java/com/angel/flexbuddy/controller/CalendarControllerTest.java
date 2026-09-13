package com.angel.flexbuddy.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.exception.GlobalExceptionHandler;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.CalendarFeedService;
import com.angel.flexbuddy.service.ScheduleService;
import com.angel.flexbuddy.service.UserTimeService;

@WebMvcTest(CalendarController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class CalendarControllerTest {

    private static final String TOKEN = "a".repeat(43);
    private static final String CALENDAR = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private CalendarFeedService feedService;

    @MockitoBean
    private UserTimeService userTime;

    @MockitoBean
    private AppUserRepository userRepository;

    @Test
    @WithAnonymousUser
    void tokenFeedIsPublic() throws Exception {
        when(feedService.feed(eq(TOKEN), anyString())).thenReturn(Optional.of(CALENDAR));

        mockMvc.perform(get("/calendar/{token}.ics", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(CALENDAR));
    }

    @Test
    @WithAnonymousUser
    void anUnknownOrRegeneratedTokenIsNotFound() throws Exception {
        when(feedService.feed(eq(TOKEN), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/calendar/{token}.ics", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void perShiftCalendarFileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/shifts/7.ics"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(feedService);
    }

    @Test
    void perShiftCalendarFileDownloadsForTheOwner() throws Exception {
        when(feedService.shift(eq("angel@example.com"), eq(7L), anyString())).thenReturn(CALENDAR);

        mockMvc.perform(get("/shifts/7.ics").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"flexbuddy-shift-7.ics\""))
                .andExpect(content().string(CALENDAR));
    }

    @Test
    void calendarRejectsAMalformedMonth() throws Exception {
        mockMvc.perform(get("/shifts/calendar").param("month", "September").with(user("angel@example.com")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scheduleService);
    }

    @Test
    void upcomingPassesTheRequestedDayCount() throws Exception {
        mockMvc.perform(get("/shifts/upcoming").param("days", "7").with(user("angel@example.com")))
                .andExpect(status().isOk());

        verify(scheduleService).upcoming("angel@example.com", 7);
    }
}
