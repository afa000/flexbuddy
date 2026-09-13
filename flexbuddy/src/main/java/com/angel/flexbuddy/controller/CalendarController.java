package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.angel.flexbuddy.dto.CalendarMonthResponse;
import com.angel.flexbuddy.dto.UpcomingResponse;
import com.angel.flexbuddy.exception.CalendarFeedNotFoundException;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.service.CalendarFeedService;
import com.angel.flexbuddy.service.ScheduleService;
import com.angel.flexbuddy.service.UserTimeService;

@RestController
public class CalendarController {

    private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar; charset=UTF-8");

    private final ScheduleService scheduleService;
    private final CalendarFeedService feedService;
    private final UserTimeService userTime;

    public CalendarController(ScheduleService scheduleService, CalendarFeedService feedService,
            UserTimeService userTime) {
        this.scheduleService = scheduleService;
        this.feedService = feedService;
        this.userTime = userTime;
    }

    @GetMapping("/shifts/calendar")
    public CalendarMonthResponse calendar(Principal principal, @RequestParam(required = false) String month) {
        YearMonth value;
        if (month == null || month.isBlank()) {
            value = YearMonth.from(userTime.today(principal.getName()));
        } else {
            try {
                value = YearMonth.parse(month.trim());
            } catch (DateTimeParseException exception) {
                throw new InvalidFilterException("month must look like 2026-09.");
            }
        }
        return scheduleService.month(principal.getName(), value);
    }

    @GetMapping("/shifts/upcoming")
    public UpcomingResponse upcoming(Principal principal, @RequestParam(defaultValue = "14") int days) {
        return scheduleService.upcoming(principal.getName(), days);
    }

    @GetMapping("/shifts/{id}.ics")
    public ResponseEntity<String> shiftCalendar(Principal principal, @PathVariable Long id) {
        String body = feedService.shift(principal.getName(), id, appUrl());
        return ResponseEntity.ok()
                .contentType(CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flexbuddy-shift-" + id + ".ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    @GetMapping("/calendar/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        String body = feedService.feed(token, appUrl()).orElseThrow(CalendarFeedNotFoundException::new);
        return ResponseEntity.ok()
                .contentType(CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"flexbuddy.ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(body);
    }

    private String appUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/")
                .queryParam("screen", "schedule").toUriString();
    }
}
