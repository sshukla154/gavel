package com.shukla.gavel.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-auction highest-bidder projection, fed by BidPlacedEvent. Exists only to detect
 * "who got outbid" when a new higher bid arrives — not a full mirror of bid-service's
 * ledger, and not the source of truth for the current price (auction-service is).
 */
@Entity
@Table(name = "highest_bidder")
public class HighestBidder {

    @Id
    @Column(name = "auction_id")
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HighestBidder() {
    }

    public HighestBidder(final UUID auctionId, final String bidderId,
                         final long amountCents, final Instant updatedAt) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amountCents = amountCents;
        this.updatedAt = updatedAt;
    }

    public UUID getAuctionId() { return auctionId; }
    public String getBidderId() { return bidderId; }
    public long getAmountCents() { return amountCents; }
    public Instant getUpdatedAt() { return updatedAt; }
}
