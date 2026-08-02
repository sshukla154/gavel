package com.shukla.gavel.auction;

import com.shukla.gavel.auction.domain.Auction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionPriceGuardTest {

    private Auction openAuction() {
        return new Auction("Guard Test", null, "seller-1", 100_000L, Instant.now().plusSeconds(3600));
    }

    @Test
    void higherBidRaisesCurrentPrice() {
        final Auction auction = openAuction();

        assertThat(auction.updateCurrentPrice(120_000L)).isTrue();
        assertThat(auction.getCurrentPriceCents()).isEqualTo(120_000L);
    }

    @Test
    void lowerBidIsRejectedAndPriceUnchanged() {
        final Auction auction = openAuction();
        auction.updateCurrentPrice(120_000L);

        assertThat(auction.updateCurrentPrice(110_000L)).isFalse();
        assertThat(auction.getCurrentPriceCents()).isEqualTo(120_000L);
    }

    @Test
    void equalBidIsRejected() {
        final Auction auction = openAuction();
        auction.updateCurrentPrice(120_000L);

        assertThat(auction.updateCurrentPrice(120_000L)).isFalse();
        assertThat(auction.getCurrentPriceCents()).isEqualTo(120_000L);
    }

    @Test
    void bidAtReservePriceIsRejected() {
        final Auction auction = openAuction();

        assertThat(auction.updateCurrentPrice(100_000L)).isFalse();
        assertThat(auction.getCurrentPriceCents()).isEqualTo(100_000L);
    }

    @Test
    void closedAuctionRejectsAnyPriceUpdate() {
        final Auction auction = openAuction();
        auction.close();

        assertThat(auction.updateCurrentPrice(200_000L)).isFalse();
        assertThat(auction.getCurrentPriceCents()).isEqualTo(100_000L);
    }
}
