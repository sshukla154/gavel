package com.shukla.gavel.auction.api;

public record BidSummary(String auctionId, String bidderId, long amountCents, String status) {}
