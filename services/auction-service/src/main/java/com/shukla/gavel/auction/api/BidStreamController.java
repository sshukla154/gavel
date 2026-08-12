package com.shukla.gavel.auction.api;

import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.auction.infrastructure.BidClient;
import com.shukla.gavel.auction.infrastructure.BidStreamBroadcaster;
import com.shukla.gavel.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Live auction room endpoints: the SSE stream and the bid-history relay backing it.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auctions")
public class BidStreamController {

    private static final long EMITTER_TIMEOUT_MILLIS = 30L * 60 * 1000;

    private final AuctionService auctionService;
    private final BidStreamBroadcaster bidStreamBroadcaster;
    private final BidClient bidClient;

    public BidStreamController(final AuctionService auctionService,
                               final BidStreamBroadcaster bidStreamBroadcaster,
                               final BidClient bidClient) {
        this.auctionService = auctionService;
        this.bidStreamBroadcaster = bidStreamBroadcaster;
        this.bidClient = bidClient;
    }

    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable final UUID id) {
        final AuctionResponse auction = auctionService.getAuction(id);
        final SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        bidStreamBroadcaster.register(id, emitter);
        sendSnapshot(emitter, auction);
        return emitter;
    }

    @GetMapping("/{id}/bids")
    public ApiResponse<List<BidSummary>> bidHistory(@PathVariable final UUID id) {
        auctionService.getAuction(id);
        return ApiResponse.of(bidClient.fetchBidsForAuction(id));
    }

    private void sendSnapshot(final SseEmitter emitter, final AuctionResponse auction) {
        final AuctionSnapshot snapshot = new AuctionSnapshot(
                auction.id(),
                auction.currentPriceCents(),
                bidStreamBroadcaster.watcherCount(auction.id()),
                fetchRecentBidsSafely(auction.id()));
        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(snapshot, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException sendFailure) {
            emitter.completeWithError(sendFailure);
        }
    }

    /**
     * The stream must open even when bid-service is down — the snapshot then carries no
     * history and the client falls back to the /bids endpoint or the live tail alone.
     */
    private List<BidSummary> fetchRecentBidsSafely(final UUID auctionId) {
        try {
            return bidClient.fetchBidsForAuction(auctionId);
        } catch (final RuntimeException bidServiceUnavailable) {
            log.warn("Bid history unavailable for auction {}: {}",
                    auctionId, bidServiceUnavailable.getMessage());
            return List.of();
        }
    }
}
