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
import com.angel.flexbuddy.repository.ExpenseRepository;

@Component
public class ShiftPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ShiftPurgeJob.class);
    private final ShiftRepository shiftRepository;
    private final ExpenseRepository expenseRepository;
    private final Clock clock;

    public ShiftPurgeJob(ShiftRepository shiftRepository, ExpenseRepository expenseRepository, Clock clock) {
        this.shiftRepository = shiftRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now(clock).minus(30, ChronoUnit.DAYS);
        int deletedExpenses = expenseRepository.purgeDeletedBefore(cutoff);
        int deleted = shiftRepository.purgeDeletedBefore(cutoff);
        if (deletedExpenses > 0) log.info("Permanently removed {} expired expenses from Recently deleted", deletedExpenses);
        if (deleted > 0) log.info("Permanently removed {} expired shifts from Recently deleted", deleted);
        return deleted + deletedExpenses;
    }
}
