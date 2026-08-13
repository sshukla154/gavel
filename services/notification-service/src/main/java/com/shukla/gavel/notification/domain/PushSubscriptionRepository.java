package com.shukla.gavel.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByBidderId(String bidderId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    // Unlike save()/findById(), a custom derived delete query is not wrapped in a
    // transaction by SimpleJpaRepository's defaults — without this it fails with
    // "No EntityManager with actual transaction available".
    @Transactional
    void deleteByEndpoint(String endpoint);
}
