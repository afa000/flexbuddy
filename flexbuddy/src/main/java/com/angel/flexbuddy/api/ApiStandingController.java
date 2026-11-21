package com.angel.flexbuddy.api;

import java.security.Principal;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.angel.flexbuddy.exception.InvalidStandingException;
import com.angel.flexbuddy.service.StandingService;

import tools.jackson.databind.ObjectMapper;

/** Read-only standing window for the native app (FB-057); see docs/api-v1.md. */
@RestController
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiStandingController {
    private static final int DEFAULT_DAYS = 90;

    private final StandingService standingService;
    private final ObjectMapper mapper;

    public ApiStandingController(StandingService standingService, ObjectMapper mapper) {
        this.standingService = standingService;
        this.mapper = mapper;
    }

    @GetMapping("/api/v1/standing")
    ResponseEntity<?> window(Principal principal, @RequestParam(name = "days", required = false) String days,
            WebRequest request) {
        int windowDays;
        try {
            windowDays = days == null ? DEFAULT_DAYS : Integer.parseInt(days);
        } catch (NumberFormatException exception) {
            return invalidDays();
        }
        return ApiEtags.withEtag(mapper, ApiStanding.of(standingService.window(principal.getName(), windowDays)),
                request);
    }

    @ExceptionHandler(InvalidStandingException.class)
    ResponseEntity<?> invalidDays() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "fields", Map.of("days", "invalid")));
    }
}
