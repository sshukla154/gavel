package com.shukla.gavel.auction.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    List<Auction> findByStatus(AuctionStatus status);
}
