package com.shukla.gavel.bid.infrastructure;

import com.shukla.gavel.bid.domain.Bid;
import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.common.event.BidPlacedEvent;
import com.shukla.gavel.common.event.PlaceBidCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class BidCommandConsumer {

    private final BidRepository bidRepository;
    private final BidEventPublisher bidEventPublisher;

    public BidCommandConsumer(final BidRepository bidRepository, final BidEventPublisher bidEventPublisher) {
        this.bidRepository = bidRepository;
        this.bidEventPublisher = bidEventPublisher;
    }

    @Transactional
    @KafkaListener(topics = "auction.bids.commands", groupId = "bid-service")
    public void consume(final PlaceBidCommand command) {
        log.debug("Received PlaceBidCommand: auctionId={} bidder={} amount={}",
                command.auctionId(), command.bidderId(), command.amountCents());

        final Bid bid = new Bid(command.auctionId(), command.bidderId(),
                command.amountCents(), command.placedAt());
        final Bid saved = bidRepository.save(bid);

        bidEventPublisher.publish(new BidPlacedEvent(
                saved.getId(), saved.getAuctionId(),
                saved.getBidderId(), saved.getAmountCents(), saved.getPlacedAt()));
    }
}
