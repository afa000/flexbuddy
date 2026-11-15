package com.angel.flexbuddy.api;

import java.io.IOException;
import java.util.Locale;

import org.apache.tomcat.util.http.fileupload.impl.SizeException;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * NA-07: the website's HiddenHttpMethodFilter reads request parameters, which makes the container parse a multipart
 * body before Spring MVC or Spring Security run, so an upload over the multipart limits fails inside the filter chain
 * and would reach the website's error page. For {@code /api/v1} multipart POSTs only, this filter parses the parts
 * first and answers a failure as JSON: {@code 413 too_large} over the limits, {@code 400 invalid} for a malformed body.
 * It runs before the token check, as the HiddenHttpMethodFilter already did, so an oversize upload without a token is
 * answered 413, not 401; nothing is decoded either way. Every other request passes straight through, so the website
 * behaves as before. Registered by {@link ApiUploadLimitConfig}.
 */
final class ApiUploadLimitFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String type = request.getContentType();
        // POST only: the HiddenHttpMethodFilter only reads parameters of a POST, and no other API method takes an upload.
        return !"POST".equals(request.getMethod()) || !path.startsWith("/api/v1/") || type == null
                || !type.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            request.getParts();
        } catch (IllegalStateException | IOException | ServletException failure) {
            // Only codes go back: the failure's message can name the upload's field and size, never its content.
            boolean tooLarge = isSizeLimit(failure);
            response.setStatus(tooLarge ? 413 : 400);
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.getWriter().write(tooLarge ? "{\"error\":\"too_large\"}" : "{\"error\":\"invalid\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isSizeLimit(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SizeException) return true;
            if (cause.getCause() == cause) break;
        }
        return false;
    }
}
