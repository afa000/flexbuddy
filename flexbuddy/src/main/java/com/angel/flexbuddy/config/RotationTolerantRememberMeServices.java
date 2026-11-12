package com.angel.flexbuddy.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * Accepts a remember-me token for a short time after it was rotated.
 * An installed app launch sends the page and service worker requests together with the same cookie. The first
 * request rotates the token, so Spring's theft check would reject the second one and sign the user out everywhere.
 * A token replaced within the grace period is answered with the current token instead; older tokens still trip
 * the theft check.
 */
public class RotationTolerantRememberMeServices extends PersistentTokenBasedRememberMeServices {

    static final Duration ROTATION_GRACE = Duration.ofMinutes(1);
    /** Set on a request whose remember-me cookie was rejected as reused. */
    static final String REJECTED_COOKIE_ATTRIBUTE = RotationTolerantRememberMeServices.class.getName() + ".REJECTED";

    private static final Logger log = LoggerFactory.getLogger(RotationTolerantRememberMeServices.class);

    private final PersistentTokenRepository tokenRepository;
    private final Clock clock;
    private final Map<String, List<RotatedToken>> recentlyRotated = new HashMap<>();

    public RotationTolerantRememberMeServices(String key, UserDetailsService userDetailsService,
            PersistentTokenRepository tokenRepository, Clock clock) {
        super(key, userDetailsService, tokenRepository);
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    /**
     * Spring removes the account's remember-me tokens and clears the cookie before it reports a reused token. The
     * request then carries on signed out, marked so the sign-in entry point can explain what happened.
     */
    @Override
    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        try {
            return super.autoLogin(request, response);
        } catch (CookieTheftException ex) {
            log.warn("Rejected a reused remember-me cookie and removed the account's remember-me tokens");
            request.setAttribute(REJECTED_COOKIE_ATTRIBUTE, Boolean.TRUE);
            return null;
        }
    }

    /** Synchronized so simultaneous requests rotate a series once instead of racing each other. */
    @Override
    protected synchronized UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
            HttpServletResponse response) {
        forgetExpiredRotations();
        if (cookieTokens.length == 2) {
            PersistentRememberMeToken current = tokenRepository.getTokenForSeries(cookieTokens[0]);
            if (current != null && !current.getTokenValue().equals(cookieTokens[1])
                    && wasRecentlyRotated(cookieTokens[0], cookieTokens[1])) {
                setCookie(new String[] {current.getSeries(), current.getTokenValue()}, getTokenValiditySeconds(),
                        request, response);
                return getUserDetailsService().loadUserByUsername(current.getUsername());
            }
        }
        UserDetails user = super.processAutoLoginCookie(cookieTokens, request, response);
        recentlyRotated.computeIfAbsent(cookieTokens[0], series -> new ArrayList<>())
                .add(new RotatedToken(cookieTokens[1], clock.instant()));
        return user;
    }

    private boolean wasRecentlyRotated(String series, String tokenValue) {
        return recentlyRotated.getOrDefault(series, List.of()).stream()
                .anyMatch(token -> token.value().equals(tokenValue));
    }

    private void forgetExpiredRotations() {
        Instant cutoff = clock.instant().minus(ROTATION_GRACE);
        recentlyRotated.values().forEach(tokens -> tokens.removeIf(token -> token.rotatedAt().isBefore(cutoff)));
        recentlyRotated.values().removeIf(List::isEmpty);
    }

    private record RotatedToken(String value, Instant rotatedAt) {
    }
}
