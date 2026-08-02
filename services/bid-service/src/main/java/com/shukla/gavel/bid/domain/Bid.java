package com.shukla.gavel.bid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bids")
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Idempotency key carried on PlaceBidCommand; unique so a redelivered command
    // cannot insert a second row.
    @Column(name = "command_id", nullable = false, unique = true)
    private UUID commandId;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    protected Bid() {
    }

    public Bid(final UUID commandId, final UUID auctionId, final String bidderId,
               final long amountCents, final Instant placedAt) {
        this.commandId = commandId;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amountCents = amountCents;
        this.placedAt = placedAt;
    }

    public UUID getId() { return id; }
    public UUID getCommandId() { return commandId; }
    public UUID getAuctionId() { return auctionId; }
    public String getBidderId() { return bidderId; }
    public long getAmountCents() { return amountCents; }
    public Instant getPlacedAt() { return placedAt; }
}
