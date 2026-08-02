package com.shukla.gavel.bid.infrastructure;

import com.shukla.gavel.bid.domain.Bid;
import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.common.event.BidPlacedEvent;
import com.shukla.gavel.common.event.PlaceBidCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BidCommandConsumer {

    private final BidRepository bidRepository;
    private final BidEventPublisher bidEventPublisher;

    public BidCommandConsumer(final BidRepository bidRepository, final BidEventPublisher bidEventPublisher) {
        this.bidRepository = bidRepository;
        this.bidEventPublisher = bidEventPublisher;
    }

    /**
     * Deliberately not @Transactional: the insert commits in its own transaction before
     * the event is published, so an emitted event always refers to a committed bid.
     * A publish failure throws, Kafka redelivers, and the commandId lookup makes the
     * redelivery idempotent — the existing row is reused and the event is retried.
     */
    @KafkaListener(topics = "auction.bids.commands", groupId = "bid-service")
    public void consume(final PlaceBidCommand command) {
        log.debug("Received PlaceBidCommand: commandId={} auctionId={} bidder={} amount={}",
                command.commandId(), command.auctionId(), command.bidderId(), command.amountCents());

        final Bid bid = bidRepository.findByCommandId(command.commandId())
                .orElseGet(() -> persistNewBid(command));

        bidEventPublisher.publish(new BidPlacedEvent(
                bid.getId(), bid.getAuctionId(),
                bid.getBidderId(), bid.getAmountCents(), bid.getPlacedAt()));
    }

    private Bid persistNewBid(final PlaceBidCommand command) {
        try {
            return bidRepository.save(new Bid(command.commandId(), command.auctionId(),
                    command.bidderId(), command.amountCents(), command.placedAt()));
        } catch (final DataIntegrityViolationException raceWithRedelivery) {
            log.debug("Concurrent insert for commandId={}, reusing existing bid", command.commandId());
            return bidRepository.findByCommandId(command.commandId()).orElseThrow();
        }
    }
}
