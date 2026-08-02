package com.shukla.gavel.auction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shukla.gavel.auction.domain.Auction;
import com.shukla.gavel.auction.domain.AuctionRepository;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import com.shukla.gavel.common.event.BidPlacedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
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

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuctionRepository auctionRepository;

    @Test
    void bidPlacedEventUpdatesAuctionCurrentPrice() throws Exception {
        final long reservePriceCents = 100_000L;
        final long bidAmountCents = 120_000L;
        final Auction auction = auctionRepository.save(
                new Auction("Test Auction", null, "seller-1", reservePriceCents, Instant.now().plusSeconds(3600)));
        final UUID auctionId = auction.getId();

        final BidPlacedEvent event = new BidPlacedEvent(
                UUID.randomUUID(), auctionId, "bidder-1", bidAmountCents, Instant.now());

        final Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        final DefaultKafkaProducerFactory<String, BidPlacedEvent> factory =
                new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), new JsonSerializer<>(objectMapper));
        final KafkaTemplate<String, BidPlacedEvent> producer = new KafkaTemplate<>(factory);
        try {
            producer.send("auction.bids.events", auctionId.toString(), event).get(5, TimeUnit.SECONDS);
        } finally {
            factory.destroy();
        }

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            final Auction updated = auctionRepository.findById(auctionId).orElseThrow();
            assertThat(updated.getCurrentPriceCents()).isEqualTo(bidAmountCents);
        });
    }
}
