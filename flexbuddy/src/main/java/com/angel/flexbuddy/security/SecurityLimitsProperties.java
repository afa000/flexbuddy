package com.angel.flexbuddy.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** How many failed sign-ins and sign-ups are allowed before the sign-in and sign-up forms lock. */
@ConfigurationProperties("flexbuddy.security")
public record SecurityLimitsProperties(@DefaultValue Login login, @DefaultValue Registration registration) {

    /** Failed sign-ins per email and per connection, and how long a lock lasts. */
    public record Login(
            @DefaultValue("5") int maxFailuresPerEmail,
            @DefaultValue("20") int maxFailuresPerIp,
            @DefaultValue("15m") Duration window,
            @DefaultValue("15m") Duration lock) {
    }

    /** Sign-up attempts per connection within one window. */
    public record Registration(
            @DefaultValue("10") int maxPerIp,
            @DefaultValue("1h") Duration window) {
    }
}
