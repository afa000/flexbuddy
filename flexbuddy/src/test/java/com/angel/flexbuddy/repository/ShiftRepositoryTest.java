package com.angel.flexbuddy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ShiftRepositoryTest {

    @Autowired ShiftRepository shiftRepository;
    @Autowired AppUserRepository userRepository;

    @Test
    void findFiltered_appliesOwnerDateStationAndSearch() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        AppUser other = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        save(angel, "VEA7", LocalDate.of(2026, 9, 5));
        save(angel, "BDL4", LocalDate.of(2026, 9, 7));
        save(angel, "VEA7", LocalDate.of(2026, 10, 1));
        save(other, "VEA7", LocalDate.of(2026, 9, 6));

        List<Shift> result = shiftRepository.findFiltered("ANGEL@example.com",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "vea7", "VEA");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getDate()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void findDistinctStations_onlyReturnsTheOwnersStations() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        AppUser other = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        save(angel, "VEA7", LocalDate.of(2026, 9, 5));
        save(angel, "BDL4", LocalDate.of(2026, 9, 7));
        save(other, "FOREIGN", LocalDate.of(2026, 9, 6));

        assertThat(shiftRepository.findDistinctStations("angel@example.com")).containsExactly("BDL4", "VEA7");
    }

    @Test
    void activeQueriesHideSoftDeletedRowsWhileTrashAndBackupQueriesCanReadThem() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        Shift active = shift(angel, "VEA7", LocalDate.of(2026, 9, 5));
        Shift deleted = shift(angel, "BDL4", LocalDate.of(2026, 9, 6));
        deleted.setDeletedAt(Instant.parse("2026-09-10T12:00:00Z"));
        deleted.setDeleteBatch("e1dbd327-2b85-4c2c-ad8e-dd8779150417");
        shiftRepository.saveAllAndFlush(List.of(active, deleted));

        assertThat(shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc("angel@example.com"))
                .extracting(Shift::getStation).containsExactly("VEA7");
        assertThat(shiftRepository.findTrash("angel@example.com"))
                .extracting(Shift::getStation).containsExactly("BDL4");
        assertThat(shiftRepository.findAllIncludingDeleted("angel@example.com"))
                .extracting(Shift::getStation).containsExactly("BDL4", "VEA7");
    }

    private void save(AppUser owner, String station, LocalDate date) {
        shiftRepository.save(shift(owner, station, date));
    }

    private Shift shift(AppUser owner, String station, LocalDate date) {
        return new Shift(null, station, date, LocalTime.of(9, 0), LocalTime.of(13, 0),
                new BigDecimal("100.00"), BigDecimal.ZERO, owner);
    }
}
