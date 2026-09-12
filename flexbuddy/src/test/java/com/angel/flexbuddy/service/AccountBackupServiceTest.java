package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class AccountBackupServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock ShiftRepository shiftRepository;

    @Test
    void createsVersionedBackupWithActiveAndDeletedCountsAndRecordsBackupTime() {
        Instant now = Instant.parse("2026-09-11T20:15:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        AppUser user = new AppUser("Angel", "angel@example.com", "secret-hash");
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        Shift active = shift(user, "VEA7");
        Shift deleted = shift(user, "BDL4");
        deleted.setDeletedAt(Instant.parse("2026-09-10T00:00:00Z"));
        when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(Optional.of(user));
        when(shiftRepository.findAllIncludingDeleted("angel@example.com")).thenReturn(List.of(active, deleted));
        AccountBackupService service = new AccountBackupService(userRepository, shiftRepository, clock, "1.2.3");

        AccountBackupFile backup = service.create("angel@example.com");

        assertThat(backup.format()).isEqualTo("flexbuddy-backup");
        assertThat(backup.version()).isEqualTo(1);
        assertThat(backup.exportedAt()).isEqualTo(now);
        assertThat(backup.account().email()).isEqualTo("angel@example.com");
        assertThat(backup.counts().shifts()).isEqualTo(1);
        assertThat(backup.counts().deletedShifts()).isEqualTo(1);
        assertThat(backup.shifts()).extracting(shift -> shift.basePay()).containsOnly("124.50");
        assertThat(user.getLastBackupAt()).isEqualTo(now);
        verify(userRepository).save(user);
    }

    private Shift shift(AppUser owner, String station) {
        Shift shift = new Shift(null, station, LocalDate.of(2026, 9, 6), LocalTime.of(4, 0),
                LocalTime.of(7, 30), new BigDecimal("124.5"), BigDecimal.ZERO, owner);
        shift.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        shift.setUpdatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        return shift;
    }
}
