package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.common.event.AuctionClosedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enforces endsAt: without this, auctions accepted bids indefinitely until a seller
 * manually closed them (placeBid checked status, never the clock). Disabled in the test
 * profile (gavel.auction.auto-close.enabled=false) so it can't fire mid-assertion in
 * unrelated tests; ITs that need it override the property and call
 * closeExpiredAuctions() directly rather than waiting on the timer.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "gavel.auction.auto-close.enabled", matchIfMissing = true)
public class AuctionAutoCloseScheduler {

    private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

    private final AuctionService auctionService;
    private final AuctionLifecycleEventPublisher lifecycleEventPublisher;
    private final BidStreamBroadcaster bidStreamBroadcaster;

    public AuctionAutoCloseScheduler(final AuctionService auctionService,
                                     final AuctionLifecycleEventPublisher lifecycleEventPublisher,
                                     final BidStreamBroadcaster bidStreamBroadcaster) {
        this.auctionService = auctionService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.bidStreamBroadcaster = bidStreamBroadcaster;
    }

    @Scheduled(fixedRate = SWEEP_INTERVAL_MILLIS)
    public void closeExpiredAuctions() {
        final List<UUID> closedIds = auctionService.closeAuctionsPastEndsAt(Instant.now());
        for (final UUID auctionId : closedIds) {
            lifecycleEventPublisher.publish(new AuctionClosedEvent(auctionId, Instant.now()));
            bidStreamBroadcaster.broadcastAuctionClosed(auctionId);
        }
    }
}
