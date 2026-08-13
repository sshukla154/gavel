package com.shukla.gavel.bid.infrastructure;

import com.shukla.gavel.common.event.BidRejectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BidRejectedEventPublisher {

    static final String TOPIC = "auction.bids.rejected";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public BidRejectedEventPublisher(final KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(final BidRejectedEvent event) {
        kafkaTemplate.send(TOPIC, event.auctionId().toString(), event);
        log.debug("Published BidRejectedEvent: auctionId={} bidder={} reason={}",
                event.auctionId(), event.bidderId(), event.reason());
    }
}
