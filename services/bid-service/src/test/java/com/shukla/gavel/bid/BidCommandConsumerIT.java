package com.shukla.gavel.bid;

import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.common.event.PlaceBidCommand;
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
class BidCommandConsumerIT {

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
    BidRepository bidRepository;

    @Test
    void placeBidCommandCreatesRowInDatabase() {
        final UUID auctionId = UUID.randomUUID();
        final UUID expectedBidderId = UUID.randomUUID();
        final PlaceBidCommand command = new PlaceBidCommand(
                auctionId, expectedBidderId.toString(), 75_000L, Instant.now());

        kafkaTemplate.send("auction.bids.commands", auctionId.toString(), command);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final var bids = bidRepository.findByAuctionId(auctionId);
            assertThat(bids).hasSize(1);
            assertThat(bids.get(0).getBidderId()).isEqualTo(expectedBidderId.toString());
            assertThat(bids.get(0).getAmountCents()).isEqualTo(75_000L);
        });
    }
}
