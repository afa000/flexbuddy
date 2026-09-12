package com.angel.flexbuddy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.TimestampListener;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, TimestampListener.class})
class ShiftRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Autowired ShiftRepository shiftRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean Clock clock;

    @BeforeEach
    void fixTheClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void persistKeepsTimestampsThatAreAlreadySetAndFillsTheOnesThatAreNot() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        Instant original = Instant.parse("2026-01-02T03:04:05Z");
        Shift restored = shift(angel, "VEA7", LocalDate.of(2026, 9, 5));
        restored.setCreatedAt(original);
        restored.setUpdatedAt(original);
        Shift created = shift(angel, "BDL4", LocalDate.of(2026, 9, 6));

        shiftRepository.saveAllAndFlush(List.of(restored, created));
        entityManager.clear();

        assertThat(shiftRepository.findById(restored.getId())).get()
                .satisfies(shift -> assertThat(shift.getCreatedAt()).isEqualTo(original))
                .satisfies(shift -> assertThat(shift.getUpdatedAt()).isEqualTo(original));
        assertThat(shiftRepository.findById(created.getId())).get()
                .satisfies(shift -> assertThat(shift.getCreatedAt()).isEqualTo(NOW))
                .satisfies(shift -> assertThat(shift.getUpdatedAt()).isEqualTo(NOW));
    }

    @Test
    void updatingAFieldMovesOnlyTheUpdatedTimestamp() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        Instant original = Instant.parse("2026-01-02T03:04:05Z");
        Shift stored = shift(angel, "VEA7", LocalDate.of(2026, 9, 5));
        stored.setCreatedAt(original);
        stored.setUpdatedAt(original);
        shiftRepository.saveAndFlush(stored);

        stored.setStation("BDL4");
        shiftRepository.saveAndFlush(stored);
        entityManager.clear();

        assertThat(shiftRepository.findById(stored.getId())).get()
                .satisfies(shift -> assertThat(shift.getCreatedAt()).isEqualTo(original))
                .satisfies(shift -> assertThat(shift.getUpdatedAt()).isEqualTo(NOW));
    }

    @Test
    void deleteAndRestoreLeaveTheUpdatedTimestampAlone() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        Instant original = Instant.parse("2026-01-02T03:04:05Z");
        Shift stored = shift(angel, "VEA7", LocalDate.of(2026, 9, 5));
        stored.setCreatedAt(original);
        stored.setUpdatedAt(original);
        shiftRepository.saveAndFlush(stored);
        entityManager.clear();

        shiftRepository.softDelete("angel@example.com", stored.getId(), NOW, "batch-1");
        entityManager.clear();
        assertThat(updatedAt("angel@example.com", stored.getId())).isEqualTo(original);

        shiftRepository.restoreDeleted("angel@example.com", stored.getId());
        entityManager.clear();
        assertThat(updatedAt("angel@example.com", stored.getId())).isEqualTo(original);

        shiftRepository.softDeleteAll("angel@example.com", NOW, "batch-2");
        entityManager.clear();
        shiftRepository.restoreBatch("angel@example.com", "batch-2");
        entityManager.clear();
        assertThat(updatedAt("angel@example.com", stored.getId())).isEqualTo(original);
    }

    @Test
    void userScopedPurgeLeavesAnotherOwnersExpiredRowsInPlace() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        AppUser other = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        Instant expired = NOW.minus(31, ChronoUnit.DAYS);
        Shift mine = shift(angel, "VEA7", LocalDate.of(2026, 7, 5));
        mine.setDeletedAt(expired);
        Shift theirs = shift(other, "FOREIGN", LocalDate.of(2026, 7, 5));
        theirs.setDeletedAt(expired);
        shiftRepository.saveAllAndFlush(List.of(mine, theirs));
        entityManager.clear();

        int purged = shiftRepository.purgeDeletedBefore("angel@example.com", NOW.minus(30, ChronoUnit.DAYS));

        assertThat(purged).isEqualTo(1);
        assertThat(shiftRepository.findTrash("other@example.com")).hasSize(1);
    }

    @Test
    void findAllIncludingDeleted_returnsSoftDeletedRows() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        Shift deleted = shift(angel, "BDL4", LocalDate.of(2026, 9, 6));
        deleted.setDeletedAt(NOW);
        shiftRepository.saveAndFlush(deleted);

        assertThat(shiftRepository.findAllIncludingDeleted("angel@example.com"))
                .extracting(Shift::getStation).containsExactly("BDL4");
    }

    private Instant updatedAt(String email, Long id) {
        return shiftRepository.findAllIncludingDeleted(email).stream()
                .filter(shift -> shift.getId().equals(id))
                .findFirst()
                .orElseThrow()
                .getUpdatedAt();
    }

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
