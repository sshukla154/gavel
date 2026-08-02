package com.shukla.gavel.bid.api;

import java.time.Instant;

public record BidSummary(String id, String auctionId, String bidderId, long amountCents, Instant placedAt) {}
