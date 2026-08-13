package com.shukla.gavel.bid.infrastructure;

import com.shukla.gavel.bid.domain.AuctionState;
import com.shukla.gavel.bid.domain.AuctionStateRepository;
import com.shukla.gavel.common.event.AuctionClosedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuctionLifecycleEventConsumer {

    private final AuctionStateRepository auctionStateRepository;

    public AuctionLifecycleEventConsumer(final AuctionStateRepository auctionStateRepository) {
        this.auctionStateRepository = auctionStateRepository;
    }

    @KafkaListener(topics = "auction.lifecycle.events", groupId = "bid-service")
    public void consume(final AuctionClosedEvent event) {
        auctionStateRepository.save(AuctionState.closed(event.auctionId(), event.closedAt()));
        log.debug("Auction marked closed in local projection: auctionId={}", event.auctionId());
    }
}
