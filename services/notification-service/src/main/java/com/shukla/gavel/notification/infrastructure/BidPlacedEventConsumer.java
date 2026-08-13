package com.shukla.gavel.notification.infrastructure;

import com.shukla.gavel.common.event.BidPlacedEvent;
import com.shukla.gavel.notification.domain.HighestBidder;
import com.shukla.gavel.notification.domain.HighestBidderRepository;
import com.shukla.gavel.notification.domain.PushSubscription;
import com.shukla.gavel.notification.domain.PushSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Maintains the highest-bidder-per-auction projection and pushes an "outbid" alert to
 * whoever held the top bid before this event.
 *
 * Idempotency/reordering: redelivery or out-of-order arrival of BidPlacedEvent is
 * handled by an upsert-if-higher guard (mirrors Auction.updateCurrentPrice's monotonic
 * guard on the auction-service side) — a stale/duplicate event with amountCents <= the
 * stored value is a no-op, never a regression. No explicit transaction/locking is needed
 * around the read-then-write: BidPlacedEvent is keyed by auctionId, so Kafka partition
 * ordering already guarantees only one thread ever processes events for a given auction
 * at a time.
 *
 * Sending the push happens after the projection write, never inside a DB transaction —
 * an HTTP call to a push service has no business holding a connection open.
 */
@Slf4j
@Component
public class BidPlacedEventConsumer {

    private final HighestBidderRepository highestBidderRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final VapidPushService vapidPushService;

    public BidPlacedEventConsumer(final HighestBidderRepository highestBidderRepository,
                                  final PushSubscriptionRepository pushSubscriptionRepository,
                                  final VapidPushService vapidPushService) {
        this.highestBidderRepository = highestBidderRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.vapidPushService = vapidPushService;
    }

    @KafkaListener(topics = "auction.bids.events", groupId = "notification-service")
    public void consume(final BidPlacedEvent event) {
        final Optional<HighestBidder> previousHighest = highestBidderRepository.findById(event.auctionId());
        if (previousHighest.isPresent() && event.amountCents() <= previousHighest.get().getAmountCents()) {
            log.debug("Ignoring stale/duplicate BidPlacedEvent: auctionId={} amount={} current={}",
                    event.auctionId(), event.amountCents(), previousHighest.get().getAmountCents());
            return;
        }

        highestBidderRepository.save(new HighestBidder(
                event.auctionId(), event.bidderId(), event.amountCents(), event.placedAt()));

        final String previousBidderId = previousHighest.map(HighestBidder::getBidderId).orElse(null);
        if (previousBidderId != null && !previousBidderId.equals(event.bidderId())) {
            notifyOutbidBidder(previousBidderId, event.auctionId());
        }
    }

    private void notifyOutbidBidder(final String bidderId, final UUID auctionId) {
        pushSubscriptionRepository.findByBidderId(bidderId)
                .forEach(subscription -> vapidPushService.sendOutbidNotification(subscription, auctionId));
    }
}
