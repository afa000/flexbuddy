package com.angel.flexbuddy.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.angel.flexbuddy.model.StandingEntry;

public interface StandingEntryRepository extends JpaRepository<StandingEntry, Long> {

    List<StandingEntry> findByOwnerEmailIgnoreCaseAndRecordedOnBetweenOrderByRecordedOnAsc(String email, LocalDate from, LocalDate to);

    Optional<StandingEntry> findFirstByOwnerEmailIgnoreCaseAndRecordedOnBeforeOrderByRecordedOnDesc(String email, LocalDate before);

    Optional<StandingEntry> findFirstByOwnerEmailIgnoreCaseOrderByRecordedOnDesc(String email);

    Optional<StandingEntry> findByOwnerEmailIgnoreCaseAndRecordedOn(String email, LocalDate recordedOn);

    List<StandingEntry> findByOwnerEmailIgnoreCaseOrderByRecordedOnAsc(String email);

    long deleteAllByOwnerId(Long ownerId);
}
