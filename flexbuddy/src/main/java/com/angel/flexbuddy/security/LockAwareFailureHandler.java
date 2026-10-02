package com.angel.flexbuddy.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sends a failed sign-in back to the form, telling the driver when that very attempt caused the lock. The failure
 * event is published before this runs, so the count is already up to date.
 */
public class LockAwareFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AttemptLimiter limiter;

    public LockAwareFailureHandler(AttemptLimiter limiter) {
        super("/login?error");
        this.limiter = limiter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String email = AttemptLimiter.emailKey(request.getParameter("username"));
        if (limiter.isLocked(AttemptLimiter.LOGIN_EMAIL, email) || limiter.isLocked(AttemptLimiter.LOGIN_IP, request.getRemoteAddr())) {
            getRedirectStrategy().sendRedirect(request, response, "/login?locked");
            return;
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
