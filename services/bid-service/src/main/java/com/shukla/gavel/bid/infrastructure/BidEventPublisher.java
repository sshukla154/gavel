package com.shukla.gavel.bid.infrastructure;

import com.shukla.gavel.common.event.BidPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BidEventPublisher {

    static final String TOPIC = "auction.bids.events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public BidEventPublisher(final KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(final BidPlacedEvent event) {
        kafkaTemplate.send(TOPIC, event.auctionId().toString(), event);
        log.debug("Published BidPlacedEvent: bidId={} auctionId={} amount={}",
                event.bidId(), event.auctionId(), event.amountCents());
    }
}
