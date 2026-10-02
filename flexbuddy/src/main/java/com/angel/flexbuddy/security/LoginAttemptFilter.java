package com.angel.flexbuddy.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses a sign-in while its email or its connection is locked, before the password is looked at. The password is
 * never checked during a lock, so the lock cannot tell a guesser they found the right one. It only sees the sign-in
 * form's POST; remember-me sign-ins and signed-in use never reach it. It is added to the security chain by
 * {@code SecurityConfig} and is deliberately not a bean, which would also register it as a servlet filter.
 */
public class LoginAttemptFilter extends OncePerRequestFilter {

    private final AttemptLimiter limiter;

    public LoginAttemptFilter(AttemptLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && (request.getContextPath() + "/login").equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String email = AttemptLimiter.emailKey(request.getParameter("username"));
        if (limiter.isLocked(AttemptLimiter.LOGIN_EMAIL, email)
                || limiter.isLocked(AttemptLimiter.LOGIN_IP, request.getRemoteAddr())) {
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }
}
