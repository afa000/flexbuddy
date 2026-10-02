package com.angel.flexbuddy.alert;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Who is emailed when something breaks, and how often. */
@ConfigurationProperties("flexbuddy.alerts")
public record ErrorAlertProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("flexbuddysupport@gmail.com") String to,
        @DefaultValue("6h") Duration quietPeriod,
        @DefaultValue("20") int maxPerDay,
        @DefaultValue("10") int browserReportsPerAccountPerHour) {
}
