package com.shukla.gavel.auction.api;

import java.time.Instant;

public record CreateAuctionRequest(
        String title,
        String description,
        long reservePriceCents,
        Instant endsAt
) {
}
