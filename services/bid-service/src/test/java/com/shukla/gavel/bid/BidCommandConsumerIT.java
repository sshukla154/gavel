package com.shukla.gavel.bid;

import com.shukla.gavel.bid.domain.AuctionStateRepository;
import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.common.event.AuctionClosedEvent;
import com.shukla.gavel.common.event.BidRejectedEvent;
import com.shukla.gavel.common.event.PlaceBidCommand;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("test")
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

    @Autowired
    AuctionStateRepository auctionStateRepository;

    @Test
    void placeBidCommandCreatesRowInDatabase() {
        final UUID auctionId = UUID.randomUUID();
        final UUID expectedBidderId = UUID.randomUUID();
        final PlaceBidCommand command = new PlaceBidCommand(
                UUID.randomUUID(), auctionId, expectedBidderId.toString(), 75_000L, Instant.now());

        kafkaTemplate.send("auction.bids.commands", auctionId.toString(), command);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            final var bids = bidRepository.findByAuctionId(auctionId);
            assertThat(bids).hasSize(1);
            assertThat(bids.get(0).getBidderId()).isEqualTo(expectedBidderId.toString());
            assertThat(bids.get(0).getAmountCents()).isEqualTo(75_000L);
        });
    }

    @Test
    void poisonMessageDoesNotBlockThePartition() {
        final UUID auctionId = UUID.randomUUID();

        // Raw string producer: bypasses the JSON serializer to plant a payload the
        // consumer cannot deserialize. Same key → same partition as the valid command.
        final DefaultKafkaProducerFactory<String, String> rawFactory =
                new DefaultKafkaProducerFactory<>(
                        Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                        new StringSerializer(), new StringSerializer());
        try {
            final KafkaTemplate<String, String> rawProducer = new KafkaTemplate<>(rawFactory);
            rawProducer.send("auction.bids.commands", auctionId.toString(), "this is not json{{{");
            rawProducer.flush();
        } finally {
            rawFactory.destroy();
        }

        final PlaceBidCommand validCommand = new PlaceBidCommand(
                UUID.randomUUID(), auctionId, "bidder-after-poison", 85_000L, Instant.now());
        kafkaTemplate.send("auction.bids.commands", auctionId.toString(), validCommand);

        // The valid command lands behind the poison message on the same partition; it can
        // only be consumed if the error handler dead-letters the poison record.
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            final var bids = bidRepository.findByAuctionId(auctionId);
            assertThat(bids).hasSize(1);
            assertThat(bids.get(0).getBidderId()).isEqualTo("bidder-after-poison");
        });
    }

    @Test
    void redeliveredCommandDoesNotCreateDuplicateBid() {
        final UUID auctionId = UUID.randomUUID();
        final PlaceBidCommand duplicated = new PlaceBidCommand(
                UUID.randomUUID(), auctionId, "bidder-redelivery", 80_000L, Instant.now());
        // Same key → same partition → ordered: once the marker's row exists, both
        // duplicate deliveries are guaranteed to have been consumed already.
        final PlaceBidCommand marker = new PlaceBidCommand(
                UUID.randomUUID(), auctionId, "bidder-marker", 90_000L, Instant.now());

        kafkaTemplate.send("auction.bids.commands", auctionId.toString(), duplicated);
        kafkaTemplate.send("auction.bids.commands", auctionId.toString(), duplicated);
        kafkaTemplate.send("auction.bids.commands", auctionId.toString(), marker);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(bidRepository.findByAuctionId(auctionId))
                        .extracting("bidderId")
                        .contains("bidder-marker"));

        assertThat(bidRepository.findByAuctionId(auctionId)).hasSize(2);
    }

    @Test
    void rejectsCommandForAuctionAlreadyMarkedClosed() throws Exception {
        final UUID auctionId = UUID.randomUUID();

        kafkaTemplate.send("auction.lifecycle.events", auctionId.toString(),
                new AuctionClosedEvent(auctionId, Instant.now()));

        // Lifecycle events land on a different topic than commands — no cross-topic
        // ordering guarantee — so this waits for the projection write before sending the
        // command, deterministically exercising the fencing path rather than the
        // documented residual race window.
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(auctionStateRepository.findById(auctionId)).isPresent());

        final UUID commandId = UUID.randomUUID();
        final PlaceBidCommand command = new PlaceBidCommand(
                commandId, auctionId, "bidder-too-late", 50_000L, Instant.now());
        kafkaTemplate.send("auction.bids.commands", auctionId.toString(), command);

        final Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-rejection-listener-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        final List<BidRejectedEvent> received = new ArrayList<>();
        try (KafkaConsumer<String, BidRejectedEvent> consumer = new KafkaConsumer<>(
                consumerProps, new StringDeserializer(), new JacksonJsonDeserializer<>(BidRejectedEvent.class))) {
            consumer.subscribe(List.of("auction.bids.rejected"));

            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                final ConsumerRecords<String, BidRejectedEvent> records = consumer.poll(Duration.ofMillis(200));
                records.forEach(record -> received.add(record.value()));
                return received.stream().anyMatch(e -> e.commandId().equals(commandId));
            });
        }

        final BidRejectedEvent rejection = received.stream()
                .filter(e -> e.commandId().equals(commandId))
                .findFirst().orElseThrow();
        assertThat(rejection.reason()).isEqualTo("AUCTION_CLOSED");
        assertThat(rejection.auctionId()).isEqualTo(auctionId);
        assertThat(bidRepository.findByAuctionId(auctionId)).isEmpty();
    }
}
