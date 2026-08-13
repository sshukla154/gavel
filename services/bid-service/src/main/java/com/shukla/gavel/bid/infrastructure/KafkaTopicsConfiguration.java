package com.shukla.gavel.bid.infrastructure;

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
    public NewTopic auctionBidsEventsTopic() {
        return TopicBuilder.name("auction.bids.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auctionBidsRejectedTopic() {
        return TopicBuilder.name("auction.bids.rejected")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter topic for the commands this service consumes. Same partition count as
     * the source so the recoverer's same-partition routing holds.
     */
    @Bean
    public NewTopic auctionBidsCommandsDeadLetterTopic() {
        return TopicBuilder.name("auction.bids.commands.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter topic for the lifecycle events this service consumes.
     */
    @Bean
    public NewTopic auctionLifecycleEventsDeadLetterTopic() {
        return TopicBuilder.name("auction.lifecycle.events.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
