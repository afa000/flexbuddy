package com.angel.flexbuddy.api;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The driver's settings for the native app (FB-059); see docs/api-v1.md. */
@RestController
@RequestMapping("/api/v1/account/settings")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiAccountSettingsController {
    private final ApiAccountSettingsService service;
    private final ObjectMapper mapper;

    public ApiAccountSettingsController(ApiAccountSettingsService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    ResponseEntity<byte[]> get(Principal principal, WebRequest request) {
        return ApiEtags.withEtag(mapper, service.get(principal.getName()), request);
    }

    @PutMapping("/{group:language|time-zone|goals|tax|reminders|mileage|payouts}")
    ResponseEntity<byte[]> put(Principal principal, @PathVariable String group, @RequestBody JsonNode body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        var saved = service.write(principal.getName(), ApiAccountSettingsService.Group.of(group), body, ifMatch);
        return ApiEtags.written(mapper, saved);
    }

    @ExceptionHandler(ApiAccountSettingsService.Conflict.class)
    ResponseEntity<?> conflict(ApiAccountSettingsService.Conflict exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "conflict");
        body.put("current", exception.current());
        return ResponseEntity.status(409).eTag(ApiEtags.tag(mapper, exception.current()))
                .header(HttpHeaders.CACHE_CONTROL, ApiEtags.cacheControl()).body(body);
    }

    @ExceptionHandler(ApiWriteReceipts.InvalidWrite.class)
    ResponseEntity<?> invalidFields(ApiWriteReceipts.InvalidWrite exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "fields", exception.fields()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ApiAccountSettingsService.Unreadable.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("error", "invalid")); }
}
