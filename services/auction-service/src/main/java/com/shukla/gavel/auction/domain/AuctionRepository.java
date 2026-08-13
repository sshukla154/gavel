package com.shukla.gavel.auction.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    List<Auction> findByStatus(AuctionStatus status);

    /**
     * Locks and returns OPEN auctions past their endsAt, skipping any row already locked
     * by a concurrent sweep on another replica — the multi-replica safety net for the
     * auto-close scheduler. Must run inside a transaction; the lock is held until commit.
     */
    @Query(value = "SELECT * FROM auctions WHERE status = 'OPEN' AND ends_at <= :cutoff "
            + "ORDER BY ends_at FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Auction> lockOpenAuctionsEndingBy(@Param("cutoff") Instant cutoff);
}
