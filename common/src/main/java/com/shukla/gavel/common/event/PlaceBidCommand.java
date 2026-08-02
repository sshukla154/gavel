package com.shukla.gavel.common.event;

import java.time.Instant;
import java.util.UUID;

public record PlaceBidCommand(
        UUID auctionId,
        String bidderId,
        long amountCents,
        Instant placedAt
) {
}
