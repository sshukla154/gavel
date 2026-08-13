package com.shukla.gavel.common.event;

import java.util.UUID;

/**
 * Published by bid-service when a PlaceBidCommand is fenced out instead of persisted.
 * {@code commandId} matches the rejected command's idempotency key. {@code reason} is a
 * free-form code (currently only "AUCTION_CLOSED") kept as a String so new rejection
 * reasons don't require a schema change.
 */
public record BidRejectedEvent(
        UUID commandId,
        UUID auctionId,
        String bidderId,
        long amountCents,
        String reason
) {
}
