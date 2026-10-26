package com.angel.flexbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
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

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(autoLogin(ORIGINAL_TOKEN, response)).isNull();
        assertThat(tokenRepository.getTokenForSeries(SERIES)).isNull();
        assertThat(response.getCookie(COOKIE).getMaxAge()).isZero();
    }

    @Test
    void tokenThatWasNeverIssuedIsTreatedAsTheft() {
        assertThat(autoLogin("forged-token", new MockHttpServletResponse())).isNull();
        assertThat(tokenRepository.getTokenForSeries(SERIES)).isNull();
    }

    @Test
    void rejectedCookieMarksTheRequestAndLogsOneWarningWithoutSecrets() {
        assertThat(autoLogin(ORIGINAL_TOKEN, new MockHttpServletResponse())).isNotNull();
        String rotatedToken = tokenRepository.getTokenForSeries(SERIES).getTokenValue();
        clock.advanceSeconds(RotationTolerantRememberMeServices.ROTATION_GRACE.toSeconds() + 1);
        MockHttpServletRequest request = request(ORIGINAL_TOKEN);

        Logger logger = (Logger) LoggerFactory.getLogger(RotationTolerantRememberMeServices.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(services.autoLogin(request, new MockHttpServletResponse())).isNull();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(request.getAttribute(RotationTolerantRememberMeServices.REJECTED_COOKIE_ATTRIBUTE)).isNotNull();
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage()).doesNotContain(USERNAME, SERIES, ORIGINAL_TOKEN, rotatedToken);
    }

    @Test
    void cookieThatIsAcceptedDoesNotMarkTheRequest() {
        MockHttpServletRequest request = request(ORIGINAL_TOKEN);

        assertThat(services.autoLogin(request, new MockHttpServletResponse())).isNotNull();

        assertThat(request.getAttribute(RotationTolerantRememberMeServices.REJECTED_COOKIE_ATTRIBUTE)).isNull();
    }

    private Authentication autoLogin(String token, MockHttpServletResponse response) {
        return services.autoLogin(request(token), response);
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE, encode(SERIES + ":" + token)));
        return request;
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
