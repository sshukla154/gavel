package com.shukla.gavel.auction.api;

import java.util.UUID;

public record PlaceBidResponse(UUID auctionId, String bidderId, long amountCents) {
}
