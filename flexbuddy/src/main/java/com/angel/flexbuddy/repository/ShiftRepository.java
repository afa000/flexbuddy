package com.angel.flexbuddy.repository;

import java.util.List;
import java.util.Optional;

import com.angel.flexbuddy.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(String email);

    Optional<Shift> findByIdAndOwnerEmailIgnoreCase(Long id, String email);
}
