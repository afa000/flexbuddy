package com.angel.flexbuddy.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Feeds the limiter from Spring Security's sign-in events. A wrong password and an email with no account both arrive
 * as the same bad-credentials failure, so unknown emails are counted and locked exactly like real ones.
 */
@Component
public class LoginAttemptEvents {

    private final AttemptLimiter limiter;

    public LoginAttemptEvents(AttemptLimiter limiter) {
        this.limiter = limiter;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        Authentication authentication = event.getAuthentication();
        limiter.record(AttemptLimiter.LOGIN_EMAIL, AttemptLimiter.emailKey(authentication.getName()));
        if (authentication.getDetails() instanceof WebAuthenticationDetails details && details.getRemoteAddress() != null) {
            limiter.record(AttemptLimiter.LOGIN_IP, details.getRemoteAddress());
        }
    }

    // Password success may still require a code. Only the final sign-in handlers clear email failures.
}
