package com.angel.flexbuddy.api;

import java.security.Principal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.angel.flexbuddy.service.AccountSettingsService;

import tools.jackson.databind.ObjectMapper;

/** Read-only calculation inputs for the native app (FB-042). */
@RestController
@RequestMapping("/api/v1/calculation-settings")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiCalculationSettingsController {
    private final AccountSettingsService settings;
    private final ObjectMapper mapper;

    public ApiCalculationSettingsController(AccountSettingsService settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @GetMapping
    ResponseEntity<byte[]> get(Principal principal, WebRequest request) {
        return ApiEtags.withEtag(mapper, ApiCalculationSettings.of(settings.get(principal.getName())), request);
    }
}
