package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.common.event.BidPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Broadcast consumer for the live bid feed. The random per-instance groupId means every
 * instance of auction-service receives every BidPlacedEvent (no partition sharing with
 * the "auction-service" price-updater group), and auto.offset.reset=latest means a fresh
 * instance starts from now — history comes from the snapshot on SSE connect, not replay.
 */
@Slf4j
@Component
public class BidStreamEventListener {

    private final BidStreamBroadcaster bidStreamBroadcaster;

    public BidStreamEventListener(final BidStreamBroadcaster bidStreamBroadcaster) {
        this.bidStreamBroadcaster = bidStreamBroadcaster;
    }

    @KafkaListener(
            topics = "auction.bids.events",
            groupId = "auction-service-stream-${random.uuid}",
            properties = "auto.offset.reset=latest")
    public void consume(final BidPlacedEvent event) {
        log.debug("Fanning out BidPlacedEvent to SSE watchers: bidId={} auctionId={}",
                event.bidId(), event.auctionId());
        bidStreamBroadcaster.broadcastBid(event);
    }
}
