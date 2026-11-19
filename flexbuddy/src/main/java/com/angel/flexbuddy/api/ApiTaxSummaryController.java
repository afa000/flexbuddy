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

import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.service.TaxService;
import com.angel.flexbuddy.service.UserTimeService;

import tools.jackson.databind.ObjectMapper;

@RestController
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiTaxSummaryController {
    private final TaxService taxService;
    private final UserTimeService userTime;
    private final ObjectMapper mapper;

    public ApiTaxSummaryController(TaxService taxService, UserTimeService userTime, ObjectMapper mapper) {
        this.taxService = taxService;
        this.userTime = userTime;
        this.mapper = mapper;
    }

    @GetMapping("/api/v1/tax/summary")
    ResponseEntity<?> summary(Principal principal, @RequestParam(name = "year", required = false) String year,
            WebRequest request) {
        String email = principal.getName();
        int taxYear;
        try {
            taxYear = year == null ? userTime.today(email).getYear() : Integer.parseInt(year);
        } catch (NumberFormatException exception) {
            return invalidYear();
        }
        return ApiEtags.withEtag(mapper, ApiTaxSummary.of(taxService.summary(email, taxYear)), request);
    }

    @ExceptionHandler(InvalidFilterException.class)
    ResponseEntity<?> invalidYear() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "fields", Map.of("year", "invalid")));
    }
}
