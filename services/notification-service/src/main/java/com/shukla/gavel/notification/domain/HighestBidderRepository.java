package com.shukla.gavel.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HighestBidderRepository extends JpaRepository<HighestBidder, UUID> {
}
