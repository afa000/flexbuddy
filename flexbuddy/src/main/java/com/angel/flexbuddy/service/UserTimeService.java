package com.angel.flexbuddy.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;

/** Resolves "now" and "today" in the signed-in driver's time zone rather than the server's. */
@Service
public class UserTimeService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of(AppUser.DEFAULT_TIME_ZONE);

    private final AppUserRepository userRepository;
    private final Clock clock;

    public UserTimeService(AppUserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public ZoneId zone(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(this::zone).orElse(DEFAULT_ZONE);
    }

    public ZoneId zone(AppUser user) {
        if (user == null || user.getTimeZone() == null) return DEFAULT_ZONE;
        try {
            return ZoneId.of(user.getTimeZone());
        } catch (DateTimeException exception) {
            return DEFAULT_ZONE;
        }
    }

    public LocalDateTime now(String email) {
        return now(zone(email));
    }

    public LocalDateTime now(ZoneId zone) {
        return LocalDateTime.ofInstant(instant(), zone);
    }

    public LocalDate today(String email) {
        return now(email).toLocalDate();
    }

    public Instant instant() {
        return clock.instant();
    }
}
