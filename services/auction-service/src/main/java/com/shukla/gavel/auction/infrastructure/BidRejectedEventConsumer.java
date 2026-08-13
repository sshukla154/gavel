package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.common.event.BidRejectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Forwards bid-service's fencing rejections onto the live SSE feed so a bidder who
 * placed a losing-race bid against a just-closed auction gets an explicit answer
 * instead of an indefinite "awaiting confirmation".
 */
@Slf4j
@Component
public class BidRejectedEventConsumer {

    private final BidStreamBroadcaster bidStreamBroadcaster;

    public BidRejectedEventConsumer(final BidStreamBroadcaster bidStreamBroadcaster) {
        this.bidStreamBroadcaster = bidStreamBroadcaster;
    }

    @KafkaListener(topics = "auction.bids.rejected", groupId = "auction-service")
    public void consume(final BidRejectedEvent event) {
        log.debug("Received BidRejectedEvent: auctionId={} bidder={} reason={}",
                event.auctionId(), event.bidderId(), event.reason());
        bidStreamBroadcaster.broadcastBidRejected(event);
    }
}
