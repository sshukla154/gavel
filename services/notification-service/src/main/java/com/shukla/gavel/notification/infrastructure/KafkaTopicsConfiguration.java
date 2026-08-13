package com.shukla.gavel.notification.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service produces. Explicit declaration removes the dependency
 * on broker auto-topic-creation, which is commonly disabled on real clusters.
 *
 * notification-service produces nothing (Web Push is not a Kafka event) — it only
 * consumes auction.bids.events, so the only bean here is that topic's dead-letter
 * counterpart. bid-service already declares auction.bids.events itself as the producer.
 */
@Configuration
public class KafkaTopicsConfiguration {

    @Bean
    public NewTopic auctionBidsEventsDeadLetterTopic() {
        return TopicBuilder.name("auction.bids.events.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
