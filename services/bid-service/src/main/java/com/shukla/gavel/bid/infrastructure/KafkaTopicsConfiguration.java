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
}
