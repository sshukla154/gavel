package com.shukla.gavel.notification;

import com.shukla.gavel.common.event.BidPlacedEvent;
import com.shukla.gavel.notification.domain.HighestBidderRepository;
import com.shukla.gavel.notification.domain.PushSubscription;
import com.shukla.gavel.notification.domain.PushSubscriptionRepository;
import com.shukla.gavel.notification.infrastructure.VapidPushService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
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
    HighestBidderRepository highestBidderRepository;

    @Autowired
    PushSubscriptionRepository pushSubscriptionRepository;

    @MockitoBean
    VapidPushService vapidPushService;

    @Test
    void firstBidCreatesProjectionAndSendsNoPush() {
        final UUID auctionId = UUID.randomUUID();
        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-1", 100_000L, Instant.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId)).isPresent());

        assertThat(highestBidderRepository.findById(auctionId).orElseThrow().getBidderId())
                .isEqualTo("bidder-1");
        verify(vapidPushService, never()).sendOutbidNotification(any(), any());
    }

    @Test
    void higherBidFromDifferentBidderNotifiesThePreviousLeader() {
        final UUID auctionId = UUID.randomUUID();
        final PushSubscription previousLeaderSubscription = pushSubscriptionRepository.save(
                new PushSubscription("bidder-outbid", "https://push.example.com/" + UUID.randomUUID(),
                        "p256dh", "auth"));

        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-outbid", 100_000L, Instant.now()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId)).isPresent());

        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-winner", 120_000L, Instant.now()));

        verify(vapidPushService, timeout(10_000))
                .sendOutbidNotification(eq(previousLeaderSubscription), eq(auctionId));
        assertThat(highestBidderRepository.findById(auctionId).orElseThrow().getBidderId())
                .isEqualTo("bidder-winner");
        assertThat(highestBidderRepository.findById(auctionId).orElseThrow().getAmountCents())
                .isEqualTo(120_000L);
    }

    @Test
    void sameBidderRaisingOwnBidSendsNoOutbidPush() {
        final UUID auctionId = UUID.randomUUID();
        pushSubscriptionRepository.save(new PushSubscription(
                "bidder-1", "https://push.example.com/" + UUID.randomUUID(), "p256dh", "auth"));

        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-1", 100_000L, Instant.now()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId)).isPresent());

        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-1", 150_000L, Instant.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId).orElseThrow().getAmountCents())
                        .isEqualTo(150_000L));
        verify(vapidPushService, never()).sendOutbidNotification(any(), any());
    }

    @Test
    void staleRedeliveredEventDoesNotRegressProjection() {
        final UUID auctionId = UUID.randomUUID();

        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-1", 100_000L, Instant.now()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId)).isPresent());

        // Same auction, same key/partition — a lower amount simulates a stale redelivery.
        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-stale", 50_000L, Instant.now()));

        // Marker on the same partition to deterministically know the stale event above
        // has already been processed (partition ordering), without a fixed sleep.
        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-marker", 100_000L, Instant.now()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId).orElseThrow().getBidderId())
                        .isEqualTo("bidder-1"));

        assertThat(highestBidderRepository.findById(auctionId).orElseThrow().getAmountCents())
                .isEqualTo(100_000L);
        verify(vapidPushService, never()).sendOutbidNotification(any(), any());
    }

    @Test
    void outbidLeaderWithNoSubscriptionDoesNotFailProcessing() {
        final UUID auctionId = UUID.randomUUID();

        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-no-subscription", 100_000L, Instant.now()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId)).isPresent());

        kafkaTemplate.send("auction.bids.events", auctionId.toString(),
                new BidPlacedEvent(UUID.randomUUID(), auctionId, "bidder-winner", 120_000L, Instant.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(highestBidderRepository.findById(auctionId).orElseThrow().getBidderId())
                        .isEqualTo("bidder-winner"));
        verify(vapidPushService, never()).sendOutbidNotification(any(), any());
    }
}
