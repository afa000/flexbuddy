package com.angel.flexbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;

class RotationTolerantRememberMeServicesTest {

    private static final String COOKIE = "remember-me";
    private static final String SERIES = "series";
    private static final String ORIGINAL_TOKEN = "original-token";
    private static final String USERNAME = "driver@example.com";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-14T19:00:00Z"));
    private InMemoryTokenRepositoryImpl tokenRepository;
    private RotationTolerantRememberMeServices services;

    @BeforeEach
    void setUp() {
        tokenRepository = new InMemoryTokenRepositoryImpl();
        tokenRepository.createNewToken(new PersistentRememberMeToken(USERNAME, SERIES, ORIGINAL_TOKEN, new Date()));
        services = new RotationTolerantRememberMeServices("test-key",
                username -> User.withUsername(username).password("unused").roles("USER").build(),
                tokenRepository, clock);
    }

    @Test
    void tokenReusedWithinGracePeriodSignsInWithTheCurrentToken() {
        assertThat(autoLogin(ORIGINAL_TOKEN, new MockHttpServletResponse())).isNotNull();
        String rotatedToken = tokenRepository.getTokenForSeries(SERIES).getTokenValue();

        clock.advanceSeconds(5);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = autoLogin(ORIGINAL_TOKEN, response);

        assertThat(authentication.getName()).isEqualTo(USERNAME);
        assertThat(tokenRepository.getTokenForSeries(SERIES).getTokenValue()).isEqualTo(rotatedToken);
        assertThat(decode(response.getCookie(COOKIE).getValue())).isEqualTo(SERIES + ":" + rotatedToken);
    }

    @Test
    void tokenReusedAfterGracePeriodIsStillTreatedAsTheft() {
        assertThat(autoLogin(ORIGINAL_TOKEN, new MockHttpServletResponse())).isNotNull();

        clock.advanceSeconds(RotationTolerantRememberMeServices.ROTATION_GRACE.toSeconds() + 1);

        assertThatThrownBy(() -> autoLogin(ORIGINAL_TOKEN, new MockHttpServletResponse()))
                .isInstanceOf(CookieTheftException.class);
        assertThat(tokenRepository.getTokenForSeries(SERIES)).isNull();
    }

    @Test
    void tokenThatWasNeverIssuedIsTreatedAsTheft() {
        assertThatThrownBy(() -> autoLogin("forged-token", new MockHttpServletResponse()))
                .isInstanceOf(CookieTheftException.class);
        assertThat(tokenRepository.getTokenForSeries(SERIES)).isNull();
    }

    private Authentication autoLogin(String token, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE, encode(SERIES + ":" + token)));
        return services.autoLogin(request, response);
    }

    private static String encode(String value) {
        return Base64.getEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Spring URL-encodes each cookie token before Base64 encoding, and generated tokens contain '=' and '+'. */
    private static String decode(String value) {
        return URLDecoder.decode(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
