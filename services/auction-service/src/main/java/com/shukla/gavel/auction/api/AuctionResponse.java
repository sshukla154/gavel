package com.shukla.gavel.auction.api;

import com.shukla.gavel.auction.domain.AuctionStatus;

import java.time.Instant;
import java.util.UUID;

public record AuctionResponse(
        UUID id,
        String title,
        String description,
        String sellerId,
        AuctionStatus status,
        long reservePriceCents,
        long currentPriceCents,
        Instant endsAt,
        Instant createdAt
) {
}
