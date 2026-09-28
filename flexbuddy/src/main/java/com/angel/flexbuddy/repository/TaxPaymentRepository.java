package com.angel.flexbuddy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.angel.flexbuddy.model.TaxPayment;

public interface TaxPaymentRepository extends JpaRepository<TaxPayment, Long> {

    List<TaxPayment> findByOwnerEmailIgnoreCaseAndTaxYearOrderByPaidOnDescIdDesc(String email, int taxYear);

    List<TaxPayment> findByOwnerEmailIgnoreCaseOrderByPaidOnAscIdAsc(String email);

    Optional<TaxPayment> findByIdAndOwnerEmailIgnoreCase(Long id, String email);

    long deleteAllByOwnerId(Long ownerId);
}
