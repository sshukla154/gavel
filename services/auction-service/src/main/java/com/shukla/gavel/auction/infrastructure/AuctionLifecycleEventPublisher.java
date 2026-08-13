package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.common.event.AuctionClosedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuctionLifecycleEventPublisher {

    static final String TOPIC = "auction.lifecycle.events";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public AuctionLifecycleEventPublisher(final KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(final AuctionClosedEvent event) {
        kafkaTemplate.send(TOPIC, event.auctionId().toString(), event);
        log.debug("Published AuctionClosedEvent: auctionId={}", event.auctionId());
    }
}
