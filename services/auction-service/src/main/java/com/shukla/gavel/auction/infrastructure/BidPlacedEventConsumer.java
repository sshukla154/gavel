package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.auction.domain.PriceUpdateResult;
import com.shukla.gavel.common.event.BidPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BidPlacedEventConsumer {

    private final AuctionService auctionService;
    private final BidStreamBroadcaster bidStreamBroadcaster;

    public BidPlacedEventConsumer(final AuctionService auctionService,
                                  final BidStreamBroadcaster bidStreamBroadcaster) {
        this.auctionService = auctionService;
        this.bidStreamBroadcaster = bidStreamBroadcaster;
    }

    @KafkaListener(topics = "auction.bids.events", groupId = "auction-service")
    public void consume(final BidPlacedEvent event) {
        log.debug("Received BidPlacedEvent: bidId={} auctionId={} amount={}",
                event.bidId(), event.auctionId(), event.amountCents());
        final PriceUpdateResult result = auctionService.updateCurrentPrice(event.auctionId(), event.amountCents());
        if (result.extended()) {
            bidStreamBroadcaster.broadcastAuctionExtended(event.auctionId(), result.endsAt());
        }
    }
}
