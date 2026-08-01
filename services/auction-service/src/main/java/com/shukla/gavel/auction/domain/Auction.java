package com.shukla.gavel.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Column(name = "reserve_price_cents", nullable = false)
    private long reservePriceCents;

    @Column(name = "current_price_cents", nullable = false)
    private long currentPriceCents;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Auction() {
    }

    public Auction(final String title,
                   final String description,
                   final String sellerId,
                   final long reservePriceCents,
                   final Instant endsAt) {
        this.title = title;
        this.description = description;
        this.sellerId = sellerId;
        this.status = AuctionStatus.OPEN;
        this.reservePriceCents = reservePriceCents;
        this.currentPriceCents = reservePriceCents;
        this.endsAt = endsAt;
        this.createdAt = Instant.now();
    }

    public void close() {
        if (this.status != AuctionStatus.OPEN) {
            throw new IllegalStateException("Cannot close auction with status: " + this.status);
        }
        this.status = AuctionStatus.CLOSED;
    }

    public void updateCurrentPrice(final long newPriceCents) {
        this.currentPriceCents = newPriceCents;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSellerId() { return sellerId; }
    public AuctionStatus getStatus() { return status; }
    public long getReservePriceCents() { return reservePriceCents; }
    public long getCurrentPriceCents() { return currentPriceCents; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getCreatedAt() { return createdAt; }
}
