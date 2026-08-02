package com.shukla.gavel.auction.api;

import java.time.Instant;

public record BidSummary(String id, String auctionId, String bidderId, long amountCents, Instant placedAt) {}
