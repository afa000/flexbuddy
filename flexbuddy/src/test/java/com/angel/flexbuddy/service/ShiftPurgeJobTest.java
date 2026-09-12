package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ShiftPurgeJobTest {

    @Mock ShiftRepository shiftRepository;

    @Test
    void purgesOnlyRowsOlderThanThirtyDays() {
        Instant now = Instant.parse("2026-09-11T12:00:00Z");
        Instant cutoff = Instant.parse("2026-08-12T12:00:00Z");
        when(shiftRepository.purgeDeletedBefore(cutoff)).thenReturn(3);
        ShiftPurgeJob job = new ShiftPurgeJob(shiftRepository, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(job.purgeExpired()).isEqualTo(3);
        verify(shiftRepository).purgeDeletedBefore(cutoff);
    }
}
