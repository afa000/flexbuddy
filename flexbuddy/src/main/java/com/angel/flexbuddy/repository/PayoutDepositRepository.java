package com.angel.flexbuddy.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.angel.flexbuddy.model.PayoutDeposit;

public interface PayoutDepositRepository extends JpaRepository<PayoutDeposit, Long> {

    List<PayoutDeposit> findByOwnerEmailIgnoreCaseAndPayoutDateBetween(String email, LocalDate from, LocalDate to);

    List<PayoutDeposit> findByOwnerEmailIgnoreCaseOrderByPayoutDateAsc(String email);

    Optional<PayoutDeposit> findByOwnerEmailIgnoreCaseAndPayoutDate(String email, LocalDate payoutDate);

    long deleteAllByOwnerId(Long ownerId);
}
