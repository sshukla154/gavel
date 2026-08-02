package com.shukla.gavel.bid.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    List<Bid> findByAuctionId(UUID auctionId);

    Optional<Bid> findByCommandId(UUID commandId);
}
