package com.shukla.gavel.auction;

import com.shukla.gavel.auction.domain.Auction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionSoftCloseTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private Auction openAuction(final Instant endsAt) {
        return new Auction("Snipe Test", null, "seller-1", 100_000L, endsAt);
    }

    @Test
    void bidInsideExtensionWindowIsWithinWindow() {
        final Instant endsAt = Instant.parse("2027-01-01T00:00:00Z");
        final Auction auction = openAuction(endsAt);

        assertThat(auction.isWithinExtensionWindow(endsAt.minusSeconds(30), WINDOW)).isTrue();
    }

    @Test
    void bidExactlyAtWindowBoundaryIsWithinWindow() {
        final Instant endsAt = Instant.parse("2027-01-01T00:00:00Z");
        final Auction auction = openAuction(endsAt);

        assertThat(auction.isWithinExtensionWindow(endsAt.minus(WINDOW), WINDOW)).isTrue();
    }

    @Test
    void bidBeforeExtensionWindowIsNotWithinWindow() {
        final Instant endsAt = Instant.parse("2027-01-01T00:00:00Z");
        final Auction auction = openAuction(endsAt);

        assertThat(auction.isWithinExtensionWindow(endsAt.minusSeconds(120), WINDOW)).isFalse();
    }

    @Test
    void closedAuctionIsNeverWithinExtensionWindow() {
        final Instant endsAt = Instant.parse("2027-01-01T00:00:00Z");
        final Auction auction = openAuction(endsAt);
        auction.close();

        assertThat(auction.isWithinExtensionWindow(endsAt.minusSeconds(1), WINDOW)).isFalse();
    }

    @Test
    void extendPushesEndsAtForward() {
        final Instant endsAt = Instant.parse("2027-01-01T00:00:00Z");
        final Auction auction = openAuction(endsAt);

        auction.extend(Duration.ofSeconds(60));

        assertThat(auction.getEndsAt()).isEqualTo(endsAt.plusSeconds(60));
    }

    @Test
    void hasEndedTrueOnlyAfterEndsAt() {
        final Instant endsAt = Instant.parse("2027-01-01T00:00:00Z");
        final Auction auction = openAuction(endsAt);

        assertThat(auction.hasEnded(endsAt.minusSeconds(1))).isFalse();
        assertThat(auction.hasEnded(endsAt)).isFalse();
        assertThat(auction.hasEnded(endsAt.plusSeconds(1))).isTrue();
    }
}
