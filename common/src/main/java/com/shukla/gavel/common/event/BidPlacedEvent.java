package com.shukla.gavel.common.event;

import java.time.Instant;
import java.util.UUID;

public record BidPlacedEvent(
        UUID bidId,
        UUID auctionId,
        String bidderId,
        long amountCents,
        Instant placedAt
) {
}
