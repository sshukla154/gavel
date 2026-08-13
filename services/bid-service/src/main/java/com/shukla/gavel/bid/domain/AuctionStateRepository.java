package com.shukla.gavel.bid.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionStateRepository extends JpaRepository<AuctionState, UUID> {
}
