package com.shukla.gavel.auction.domain;

import java.time.Instant;

/**
 * Outcome of applying a confirmed bid's price to its auction. {@code extended} signals
 * the caller (BidPlacedEventConsumer) to push the new {@code endsAt} onto the live SSE
 * feed — the anti-snipe countdown jump.
 */
public record PriceUpdateResult(boolean priceUpdated, boolean extended, Instant endsAt) {

    public static PriceUpdateResult notFound() {
        return new PriceUpdateResult(false, false, null);
    }
}
