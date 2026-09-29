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
import com.angel.flexbuddy.model.PushSubscription;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
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
    @Autowired PushSubscriptionRepository pushSubscriptionRepository;
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
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "vea7", "VEA", ShiftStatus.HISTORY);

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

    @Test
    void findFiltered_returnsOnlyTheRequestedStatuses() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        saveWithStatus(angel, LocalDate.of(2026, 9, 5), ShiftStatus.COMPLETED);
        saveWithStatus(angel, LocalDate.of(2026, 9, 6), ShiftStatus.CANCELLED);
        saveWithStatus(angel, LocalDate.of(2026, 9, 7), ShiftStatus.FORFEITED);
        saveWithStatus(angel, LocalDate.of(2026, 9, 20), ShiftStatus.SCHEDULED);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        assertThat(shiftRepository.findFiltered("angel@example.com", from, to, "", "", ShiftStatus.HISTORY))
                .extracting(Shift::getStatus)
                .containsExactlyInAnyOrder(ShiftStatus.COMPLETED, ShiftStatus.CANCELLED, ShiftStatus.FORFEITED);
        assertThat(shiftRepository.findFiltered("angel@example.com", from, to, "", "", ShiftStatus.EARNINGS))
                .extracting(Shift::getStatus)
                .containsExactlyInAnyOrder(ShiftStatus.COMPLETED, ShiftStatus.CANCELLED);
        assertThat(shiftRepository.findFiltered("angel@example.com", from, to, "", "", ShiftStatus.ALL)).hasSize(4);
    }

    @Test
    void forfeitedAndCancelledInRangeSkipsTrashOtherStatusesAndOtherOwners() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        AppUser other = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        saveWithStatus(angel, LocalDate.of(2026, 9, 3), ShiftStatus.COMPLETED);
        saveWithStatus(angel, LocalDate.of(2026, 9, 4), ShiftStatus.SCHEDULED);
        Shift late = shift(angel, "LATE1", LocalDate.of(2026, 9, 8));
        late.setStatus(ShiftStatus.FORFEITED);
        late.setLateForfeit(true);
        shiftRepository.save(late);
        saveWithStatus(angel, LocalDate.of(2026, 9, 9), ShiftStatus.CANCELLED);
        Shift trashed = shift(angel, "TRASH", LocalDate.of(2026, 9, 10));
        trashed.setStatus(ShiftStatus.FORFEITED);
        trashed.setDeletedAt(NOW);
        shiftRepository.save(trashed);
        saveWithStatus(angel, LocalDate.of(2026, 8, 1), ShiftStatus.FORFEITED);
        saveWithStatus(other, LocalDate.of(2026, 9, 8), ShiftStatus.FORFEITED);
        entityManager.flush();
        entityManager.clear();

        assertThat(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusInAndDateBetweenOrderByDateAscStartTimeAsc(
                "ANGEL@example.com", java.util.Set.of(ShiftStatus.FORFEITED, ShiftStatus.CANCELLED),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .extracting(shift -> shift.getDate() + " " + shift.getStatus() + " " + shift.isLateForfeit())
                .containsExactly("2026-09-08 FORFEITED true", "2026-09-09 CANCELLED false");
    }

    @Test
    void scheduledLookupIsOwnerScopedAndOrderedByDate() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        AppUser other = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        saveWithStatus(angel, LocalDate.of(2026, 9, 14), ShiftStatus.SCHEDULED);
        saveWithStatus(angel, LocalDate.of(2026, 9, 13), ShiftStatus.SCHEDULED);
        saveWithStatus(angel, LocalDate.of(2026, 9, 13), ShiftStatus.COMPLETED);
        saveWithStatus(other, LocalDate.of(2026, 9, 13), ShiftStatus.SCHEDULED);

        assertThat(shiftRepository.findByOwnerEmailIgnoreCaseAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(
                "ANGEL@example.com", ShiftStatus.SCHEDULED, LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 14)))
                .extracting(Shift::getDate)
                .containsExactly(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 14));
    }

    @Test
    void reminderCandidatesNeedALeadTimeAndAPushSubscription() {
        AppUser subscribed = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        subscribed.setRemindBeforeMinutes(60);
        AppUser withoutPush = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        withoutPush.setRemindBeforeMinutes(60);
        PushSubscription subscription = new PushSubscription();
        subscription.setOwner(subscribed);
        subscription.setEndpoint("https://fcm.googleapis.com/fcm/send/abc");
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        subscription.setCreatedAt(NOW);
        pushSubscriptionRepository.save(subscription);
        saveWithStatus(subscribed, LocalDate.of(2026, 9, 12), ShiftStatus.SCHEDULED);
        saveWithStatus(subscribed, LocalDate.of(2026, 9, 12), ShiftStatus.COMPLETED);
        saveWithStatus(withoutPush, LocalDate.of(2026, 9, 12), ShiftStatus.SCHEDULED);
        entityManager.flush();
        entityManager.clear();

        assertThat(shiftRepository.findScheduledWithLeadTime(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13)))
                .extracting(shift -> shift.getOwner().getEmail())
                .containsExactly("angel@example.com");
        assertThat(shiftRepository.findScheduledWithConfirmNudges(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13)))
                .isEmpty();
    }

    private void saveWithStatus(AppUser owner, LocalDate date, ShiftStatus status) {
        Shift shift = shift(owner, "VEA7", date);
        shift.setStatus(status);
        shiftRepository.save(shift);
    }

    @Test
    void latestOdometerSkipsScheduledDeletedAndLaterBlocks() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        Shift older = withOdometer(shift(angel, "VEA7", LocalDate.of(2026, 9, 3)), "45100.0");
        Shift latest = withOdometer(shift(angel, "DAX5", LocalDate.of(2026, 9, 5)), "45210.4");
        Shift deleted = withOdometer(shift(angel, "VEA7", LocalDate.of(2026, 9, 6)), "45300.0");
        deleted.setDeletedAt(Instant.parse("2026-09-10T12:00:00Z"));
        Shift scheduled = withOdometer(shift(angel, "VEA7", LocalDate.of(2026, 9, 7)), "45400.0");
        scheduled.setStatus(ShiftStatus.SCHEDULED);
        Shift later = withOdometer(shift(angel, "VEA7", LocalDate.of(2026, 9, 9)), "45500.0");
        shiftRepository.saveAllAndFlush(List.of(older, latest, deleted, scheduled, later));
        entityManager.clear();

        List<Shift> found = shiftRepository.findWithOdometerBefore("angel@example.com", LocalDate.of(2026, 9, 9),
                LocalTime.of(9, 0), org.springframework.data.domain.PageRequest.of(0, 1));

        assertThat(found).singleElement().satisfies(shift -> {
            assertThat(shift.getStation()).isEqualTo("DAX5");
            assertThat(shift.getOdometerEnd()).isEqualByComparingTo("45210.4");
        });
    }

    private static Shift withOdometer(Shift shift, String end) {
        shift.setOdometerEnd(new BigDecimal(end));
        return shift;
    }

    @Test
    void milesNudgeCandidatesHaveNoMilesAndAnOwnerWhoAskedForPush() {
        AppUser subscribed = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        subscribed.setRemindMiles(true);
        AppUser optedOut = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        for (AppUser owner : List.of(subscribed, optedOut)) {
            PushSubscription subscription = new PushSubscription();
            subscription.setOwner(owner);
            subscription.setEndpoint("https://fcm.googleapis.com/fcm/send/" + owner.getEmail());
            subscription.setP256dh("key");
            subscription.setAuth("auth");
            subscription.setCreatedAt(NOW);
            pushSubscriptionRepository.save(subscription);
        }
        saveWithStatus(subscribed, LocalDate.of(2026, 9, 12), ShiftStatus.COMPLETED);
        saveWithStatus(subscribed, LocalDate.of(2026, 9, 12), ShiftStatus.SCHEDULED);
        saveWithStatus(subscribed, LocalDate.of(2026, 9, 12), ShiftStatus.CANCELLED);
        Shift logged = shift(subscribed, "LOGGED", LocalDate.of(2026, 9, 12));
        logged.setMiles(BigDecimal.ZERO);
        shiftRepository.save(logged);
        saveWithStatus(optedOut, LocalDate.of(2026, 9, 12), ShiftStatus.COMPLETED);
        entityManager.flush();
        entityManager.clear();

        assertThat(shiftRepository.findMissingMilesWithNudges(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13)))
                .extracting(shift -> shift.getOwner().getEmail() + " " + shift.getStatus())
                .containsExactlyInAnyOrder("angel@example.com COMPLETED", "angel@example.com SCHEDULED");
    }

    @Test
    void findByCreateRequestIdIncludingDeletedFindsTrashedRowsAndOnlyTheOwners() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        AppUser other = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        String key = "11111111-1111-4111-8111-111111111111";
        Shift mine = shift(angel, "VEA7", LocalDate.of(2026, 9, 5));
        mine.setCreateRequestId(key);
        mine.setDeletedAt(NOW);
        shiftRepository.save(mine);
        Shift theirs = shift(other, "BDL4", LocalDate.of(2026, 9, 5));
        theirs.setCreateRequestId("22222222-2222-4222-8222-222222222222");
        shiftRepository.save(theirs);
        entityManager.flush();
        entityManager.clear();

        assertThat(shiftRepository.findByCreateRequestIdIncludingDeleted("ANGEL@example.com", key))
                .get().extracting(Shift::getStation).isEqualTo("VEA7");
        assertThat(shiftRepository.findByCreateRequestIdIncludingDeleted("angel@example.com",
                "22222222-2222-4222-8222-222222222222")).isEmpty();
        assertThat(shiftRepository.findByCreateRequestIdIncludingDeleted("other@example.com", key)).isEmpty();
    }

    @Test
    void theSameKeyCannotCreateTwoShiftsForOneOwner() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        String key = "11111111-1111-4111-8111-111111111111";
        Shift first = shift(angel, "VEA7", LocalDate.of(2026, 9, 5));
        first.setCreateRequestId(key);
        shiftRepository.saveAndFlush(first);
        Shift second = shift(angel, "BDL4", LocalDate.of(2026, 9, 6));
        second.setCreateRequestId(key);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> shiftRepository.saveAndFlush(second))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
