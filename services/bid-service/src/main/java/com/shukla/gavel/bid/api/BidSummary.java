package com.shukla.gavel.bid.api;

public record BidSummary(String auctionId, String bidderId, long amountCents, String status) {}
