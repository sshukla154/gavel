package com.shukla.gavel.bid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Local projection of auction lifecycle fed by AuctionClosedEvent — not a mirror of
 * auction-service's Auction, just enough to fence PlaceBidCommand for closed auctions.
 * Absence of a row means "open as far as this service knows".
 */
@Entity
@Table(name = "auction_state")
public class AuctionState {

    static final String STATUS_CLOSED = "CLOSED";

    @Id
    @Column(name = "auction_id")
    private UUID auctionId;

    @Column(nullable = false)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuctionState() {
    }

    private AuctionState(final UUID auctionId, final String status, final Instant updatedAt) {
        this.auctionId = auctionId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public static AuctionState closed(final UUID auctionId, final Instant closedAt) {
        return new AuctionState(auctionId, STATUS_CLOSED, closedAt);
    }

    public boolean isClosed() {
        return STATUS_CLOSED.equals(this.status);
    }

    public UUID getAuctionId() { return auctionId; }
    public String getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
