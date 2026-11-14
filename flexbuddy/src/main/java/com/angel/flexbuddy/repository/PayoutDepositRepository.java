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

    /** Lock the recorded payout, if any, while an API write checks its version and changes it (NA-06). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select d from PayoutDeposit d where lower(d.owner.email) = lower(:email) and d.payoutDate = :payoutDate
            """)
    Optional<PayoutDeposit> lockByOwnerAndDate(@org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("payoutDate") LocalDate payoutDate);
}
