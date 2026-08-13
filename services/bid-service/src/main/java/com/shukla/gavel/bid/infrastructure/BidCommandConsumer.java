package com.shukla.gavel.bid.infrastructure;

import com.shukla.gavel.bid.domain.AuctionState;
import com.shukla.gavel.bid.domain.AuctionStateRepository;
import com.shukla.gavel.bid.domain.Bid;
import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.common.event.BidPlacedEvent;
import com.shukla.gavel.common.event.BidRejectedEvent;
import com.shukla.gavel.common.event.PlaceBidCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class BidCommandConsumer {

    private final BidRepository bidRepository;
    private final BidEventPublisher bidEventPublisher;
    private final AuctionStateRepository auctionStateRepository;
    private final BidRejectedEventPublisher bidRejectedEventPublisher;

    public BidCommandConsumer(final BidRepository bidRepository,
                              final BidEventPublisher bidEventPublisher,
                              final AuctionStateRepository auctionStateRepository,
                              final BidRejectedEventPublisher bidRejectedEventPublisher) {
        this.bidRepository = bidRepository;
        this.bidEventPublisher = bidEventPublisher;
        this.auctionStateRepository = auctionStateRepository;
        this.bidRejectedEventPublisher = bidRejectedEventPublisher;
    }

    /**
     * Deliberately not @Transactional: the insert commits in its own transaction before
     * the event is published, so an emitted event always refers to a committed bid.
     * A publish failure throws, Kafka redelivers, and the commandId lookup makes the
     * redelivery idempotent — the existing row is reused and the event is retried.
     *
     * Fencing: the auction_state projection is fed by AuctionClosedEvent on a separate
     * topic, so there is no cross-topic ordering guarantee against this command — a bid
     * sent just before close can still slip through here if the lifecycle event hasn't
     * landed yet (documented residual race in ADR 0012). auction-service's own monotonic
     * price guard is the backstop: a bid that slips through here is persisted in the
     * ledger but never raises the price or reaches the live feed once the auction is
     * closed on the auction-service side.
     */
    @KafkaListener(topics = "auction.bids.commands", groupId = "bid-service")
    public void consume(final PlaceBidCommand command) {
        log.debug("Received PlaceBidCommand: commandId={} auctionId={} bidder={} amount={}",
                command.commandId(), command.auctionId(), command.bidderId(), command.amountCents());

        if (isAuctionClosed(command.auctionId())) {
            log.debug("Rejecting bid for closed auction: commandId={} auctionId={}",
                    command.commandId(), command.auctionId());
            bidRejectedEventPublisher.publish(new BidRejectedEvent(
                    command.commandId(), command.auctionId(), command.bidderId(),
                    command.amountCents(), "AUCTION_CLOSED"));
            return;
        }

        final Bid bid = bidRepository.findByCommandId(command.commandId())
                .orElseGet(() -> persistNewBid(command));

        bidEventPublisher.publish(new BidPlacedEvent(
                bid.getId(), bid.getAuctionId(),
                bid.getBidderId(), bid.getAmountCents(), bid.getPlacedAt()));
    }

    private boolean isAuctionClosed(final UUID auctionId) {
        return auctionStateRepository.findById(auctionId)
                .map(AuctionState::isClosed)
                .orElse(false);
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
