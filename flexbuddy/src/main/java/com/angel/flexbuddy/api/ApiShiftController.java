package com.angel.flexbuddy.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.angel.flexbuddy.exception.ShiftNotFoundException;

import tools.jackson.databind.ObjectMapper;

/** Read-only blocks and Home for the native app (FB-033); see docs/api-v1.md. */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiShiftController {
    /** Phones keep their own copy; nothing in between may, and every use revalidates with the ETag. */
    private static final String CACHE_CONTROL = "private, no-cache";

    private final ApiShiftService service;
    private final ObjectMapper mapper;

    public ApiShiftController(ApiShiftService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/shifts")
    ResponseEntity<byte[]> shifts(Principal principal, @RequestParam(required = false) String updatedSince,
            WebRequest request) {
        Instant since = updatedSince == null ? null : SyncCursor.decode(updatedSince);
        return withEtag(service.sync(principal.getName(), since), request);
    }

    @GetMapping("/shifts/{id}")
    ResponseEntity<byte[]> shift(Principal principal, @PathVariable long id, WebRequest request) {
        return withEtag(service.shift(principal.getName(), id), request);
    }

    @GetMapping("/home")
    ResponseEntity<byte[]> home(Principal principal, WebRequest request) {
        return withEtag(service.home(principal.getName()), request);
    }

    /** A strong ETag over the exact bytes sent; a matching If-None-Match gets 304 with no body. */
    private ResponseEntity<byte[]> withEtag(Object body, WebRequest request) {
        byte[] json = mapper.writeValueAsBytes(body);
        String etag = "\"" + sha256(json) + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304).eTag(etag).header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL).build();
        }
        return ResponseEntity.ok().eTag(etag).header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .contentType(MediaType.APPLICATION_JSON).body(json);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @ExceptionHandler(ShiftNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(404).body(Map.of("error", "not_found"));
    }

    /** An undecodable cursor or a path id that isn't a number. */
    @ExceptionHandler({SyncCursor.InvalidCursorException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid"));
    }
}
