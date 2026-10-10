package com.angel.flexbuddy.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Sends a failed Google sign-in back to the sign-in page with a reason the page can explain. */
@Component
public class GoogleFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        response.sendRedirect("/login?google=" + reason(exception));
    }

    static String reason(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth) {
            return switch (oauth.getError().getErrorCode()) {
                case GoogleAccountService.UNVERIFIED_EMAIL -> "unverified";
                case GoogleAccountService.LINKED_ELSEWHERE -> "linked";
                case GoogleAccountService.TOO_MANY_SIGNUPS -> "limit";
                default -> "failed";
            };
        }
        return "failed";
    }
}
