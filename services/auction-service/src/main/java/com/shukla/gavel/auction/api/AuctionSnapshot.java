package com.shukla.gavel.auction.api;

import java.util.List;
import java.util.UUID;

/**
 * First event sent on every SSE connect (and reconnect): the state the live tail builds
 * on. Clients render from the snapshot and then apply incoming bid events.
 */
public record AuctionSnapshot(
        UUID auctionId,
        long currentPriceCents,
        int watchers,
        List<BidSummary> recentBids
) {
}
