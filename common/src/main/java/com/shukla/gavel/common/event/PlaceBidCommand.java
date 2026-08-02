package com.shukla.gavel.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Command asking bid-service to record a bid. {@code commandId} is generated once by the
 * originating service and is the idempotency key: Kafka redelivery of the same command
 * must not create a second bid.
 */
public record PlaceBidCommand(
        UUID commandId,
        UUID auctionId,
        String bidderId,
        long amountCents,
        Instant placedAt
) {
}
