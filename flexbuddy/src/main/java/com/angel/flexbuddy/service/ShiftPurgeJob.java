package com.angel.flexbuddy.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.repository.ShiftRepository;

@Component
public class ShiftPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ShiftPurgeJob.class);
    private final ShiftRepository shiftRepository;
    private final Clock clock;

    public ShiftPurgeJob(ShiftRepository shiftRepository, Clock clock) {
        this.shiftRepository = shiftRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public int purgeExpired() {
        int deleted = shiftRepository.purgeDeletedBefore(Instant.now(clock).minus(30, ChronoUnit.DAYS));
        if (deleted > 0) log.info("Permanently removed {} expired shifts from Recently deleted", deleted);
        return deleted;
    }
}
