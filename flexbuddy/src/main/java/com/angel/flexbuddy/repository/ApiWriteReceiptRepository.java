package com.angel.flexbuddy.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.angel.flexbuddy.model.ApiWriteReceipt;

public interface ApiWriteReceiptRepository extends JpaRepository<ApiWriteReceipt, Long> {
    Optional<ApiWriteReceipt> findByOwnerIdAndRequestKey(Long ownerId, String requestKey);
}
