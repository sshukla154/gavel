package com.shukla.gavel.auction;

import com.shukla.gavel.auction.domain.Auction;
import com.shukla.gavel.auction.domain.AuctionRepository;
import com.shukla.gavel.common.event.BidPlacedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class BidPlacedEventConsumerIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.2"))
            .withKraft();

    @DynamicPropertySource
    static void infrastructure(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
    }

    @Autowired
    KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    AuctionRepository auctionRepository;

    @Test
    void bidPlacedEventUpdatesAuctionCurrentPrice() {
        final long reservePriceCents = 100_000L;
        final long bidAmountCents = 120_000L;
        final Auction auction = auctionRepository.save(
                new Auction("Test Auction", null, "seller-1", reservePriceCents, Instant.now().plusSeconds(3600)));
        final UUID auctionId = auction.getId();

        final BidPlacedEvent event = new BidPlacedEvent(
                UUID.randomUUID(), auctionId, "bidder-1", bidAmountCents, Instant.now());

        kafkaTemplate.send("auction.bids.events", auctionId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final Auction updated = auctionRepository.findById(auctionId).orElseThrow();
            assertThat(updated.getCurrentPriceCents()).isEqualTo(bidAmountCents);
        });
    }
}
