package com.shukla.gavel.auction;

import com.shukla.gavel.auction.api.PlaceBidRequest;
import com.shukla.gavel.auction.domain.Auction;
import com.shukla.gavel.auction.domain.AuctionRepository;
import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.auction.domain.PriceUpdateResult;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for AuctionService's closing correctness — no Spring context, no
 * Kafka: AuctionRepository and BidCommandPublisher are plain Mockito mocks.
 */
class AuctionServiceClosingTest {

    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final BidCommandPublisher bidCommandPublisher = mock(BidCommandPublisher.class);
    private final AuctionService auctionService = new AuctionService(auctionRepository, bidCommandPublisher);

    @Test
    void placeBidRejectsWhenAuctionHasEndedEvenIfStillMarkedOpen() {
        final UUID auctionId = UUID.randomUUID();
        final Auction expiredButOpen = new Auction(
                "Expired", null, "seller-1", 10_000L, Instant.now().minusSeconds(5));
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(expiredButOpen));

        assertThatThrownBy(() -> auctionService.placeBid(auctionId, "bidder-1", new PlaceBidRequest(20_000L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ended");
        verifyNoInteractions(bidCommandPublisher);
    }

    @Test
    void placeBidAcceptsWhenAuctionIsOpenAndNotYetEnded() {
        final UUID auctionId = UUID.randomUUID();
        final Auction openAuction = new Auction(
                "Still Open", null, "seller-1", 10_000L, Instant.now().plusSeconds(3600));
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(openAuction));

        auctionService.placeBid(auctionId, "bidder-1", new PlaceBidRequest(20_000L));

        org.mockito.Mockito.verify(bidCommandPublisher).publish(any());
    }

    @Test
    void closeAuctionsPastEndsAtClosesEachLockedAuctionAndReturnsItsId() {
        final Auction dueAuction = new Auction(
                "Due", null, "seller-1", 10_000L, Instant.now().minusSeconds(1));
        when(auctionRepository.lockOpenAuctionsEndingBy(any())).thenReturn(List.of(dueAuction));

        final List<UUID> closedIds = auctionService.closeAuctionsPastEndsAt(Instant.now());

        assertThat(closedIds).containsExactly(dueAuction.getId());
        assertThat(dueAuction.getStatus().name()).isEqualTo("CLOSED");
    }

    @Test
    void closeAuctionsPastEndsAtReturnsEmptyWhenNothingIsDue() {
        when(auctionRepository.lockOpenAuctionsEndingBy(any())).thenReturn(List.of());

        assertThat(auctionService.closeAuctionsPastEndsAt(Instant.now())).isEmpty();
    }

    @Test
    void updateCurrentPriceExtendsEndsAtWhenBidLandsInsideSnipeWindow() {
        final UUID auctionId = UUID.randomUUID();
        final Instant endsAt = Instant.now().plusSeconds(30);
        final Auction auction = new Auction("Snipe", null, "seller-1", 10_000L, endsAt);
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        final PriceUpdateResult result = auctionService.updateCurrentPrice(auctionId, 20_000L);

        assertThat(result.priceUpdated()).isTrue();
        assertThat(result.extended()).isTrue();
        assertThat(result.endsAt()).isEqualTo(endsAt.plusSeconds(60));
    }

    @Test
    void updateCurrentPriceDoesNotExtendWhenBidLandsOutsideSnipeWindow() {
        final UUID auctionId = UUID.randomUUID();
        final Instant endsAt = Instant.now().plusSeconds(3600);
        final Auction auction = new Auction("No Snipe", null, "seller-1", 10_000L, endsAt);
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        final PriceUpdateResult result = auctionService.updateCurrentPrice(auctionId, 20_000L);

        assertThat(result.priceUpdated()).isTrue();
        assertThat(result.extended()).isFalse();
        assertThat(result.endsAt()).isEqualTo(endsAt);
    }

    @Test
    void updateCurrentPriceDoesNotExtendWhenPriceUpdateIsRejected() {
        final UUID auctionId = UUID.randomUUID();
        final Instant endsAt = Instant.now().plusSeconds(30);
        final Auction auction = new Auction("Rejected Bid", null, "seller-1", 50_000L, endsAt);
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        // Bid below current price: rejected by the monotonic guard, must not extend.
        final PriceUpdateResult result = auctionService.updateCurrentPrice(auctionId, 10_000L);

        assertThat(result.priceUpdated()).isFalse();
        assertThat(result.extended()).isFalse();
    }
}
