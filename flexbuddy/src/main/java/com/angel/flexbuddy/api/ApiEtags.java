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
        String etag = tag(json);
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304).eTag(etag).header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL).build();
        }
        return ResponseEntity.ok().eTag(etag).header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /** The tag a read of {@code body} carries, so a write can compare it with the phone's If-Match. */
    static String tag(ObjectMapper mapper, Object body) {
        return tag(mapper.writeValueAsBytes(body));
    }

    /** A write's answer: the body as a read would send it, with that read's tag, never a 304. */
    static ResponseEntity<byte[]> written(ObjectMapper mapper, Object body) {
        byte[] json = mapper.writeValueAsBytes(body);
        return ResponseEntity.ok().eTag(tag(json)).header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .contentType(MediaType.APPLICATION_JSON).body(json);
    }

    static String cacheControl() {
        return CACHE_CONTROL;
    }

    private static String tag(byte[] json) {
        return "\"" + sha256(json) + "\"";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
