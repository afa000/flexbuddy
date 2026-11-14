package com.angel.flexbuddy.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import tools.jackson.databind.ObjectMapper;

/** Read answers with a strong ETag over the exact bytes sent; a matching If-None-Match gets 304 with no body. */
final class ApiEtags {
    /** Phones keep their own copy; nothing in between may, and every use revalidates with the ETag. */
    private static final String CACHE_CONTROL = "private, no-cache";

    private ApiEtags() {
    }

    static ResponseEntity<byte[]> withEtag(ObjectMapper mapper, Object body, WebRequest request) {
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
}
