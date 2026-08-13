package com.shukla.gavel.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by auction-service whenever an auction transitions to CLOSED (manual close or
 * the auto-close scheduler at endsAt). bid-service consumes this into a local
 * auction_state projection so BidCommandConsumer can fence out commands for auctions that
 * have already closed.
 */
public record AuctionClosedEvent(
        UUID auctionId,
        Instant closedAt
) {
}
