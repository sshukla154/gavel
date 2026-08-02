package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.common.event.PlaceBidCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BidCommandPublisher {

    static final String TOPIC = "auction.bids.commands";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public BidCommandPublisher(final KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(final PlaceBidCommand command) {
        kafkaTemplate.send(TOPIC, command.auctionId().toString(), command);
        log.debug("Published PlaceBidCommand: auctionId={} bidder={} amount={}",
                command.auctionId(), command.bidderId(), command.amountCents());
    }
}
