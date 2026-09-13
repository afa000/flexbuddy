package com.angel.flexbuddy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.angel.flexbuddy.model.PushSubscription;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByOwnerId(Long ownerId);

    long deleteByEndpointAndOwnerEmailIgnoreCase(String endpoint, String email);
}
