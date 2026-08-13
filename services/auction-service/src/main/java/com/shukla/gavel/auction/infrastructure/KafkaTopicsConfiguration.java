package com.shukla.gavel.auction.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service produces. Explicit declaration removes the dependency
 * on broker auto-topic-creation, which is commonly disabled on real clusters.
 */
@Configuration
public class KafkaTopicsConfiguration {

    @Bean
    public NewTopic auctionBidsCommandsTopic() {
        return TopicBuilder.name("auction.bids.commands")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auctionLifecycleEventsTopic() {
        return TopicBuilder.name("auction.lifecycle.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter topic for the events this service consumes. Same partition count as
     * the source so the recoverer's same-partition routing holds.
     */
    @Bean
    public NewTopic auctionBidsEventsDeadLetterTopic() {
        return TopicBuilder.name("auction.bids.events.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter topic for the bid-rejection notices this service consumes.
     */
    @Bean
    public NewTopic auctionBidsRejectedDeadLetterTopic() {
        return TopicBuilder.name("auction.bids.rejected.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
