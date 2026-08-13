package com.shukla.gavel.auction;

import com.shukla.gavel.auction.domain.Auction;
import com.shukla.gavel.auction.domain.AuctionRepository;
import com.shukla.gavel.auction.domain.AuctionStatus;
import com.shukla.gavel.auction.infrastructure.AuctionAutoCloseScheduler;
import com.shukla.gavel.auction.infrastructure.AuctionLifecycleEventPublisher;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import com.shukla.gavel.common.event.AuctionClosedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class AuctionAutoCloseSchedulerIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configure(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Only this test needs the scheduler bean to exist; it calls the bean's method
        // directly rather than waiting on the real @Scheduled timer.
        registry.add("gavel.auction.auto-close.enabled", () -> "true");
    }

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @MockitoBean
    AuctionLifecycleEventPublisher lifecycleEventPublisher;

    @Autowired
    AuctionRepository auctionRepository;

    @Autowired
    AuctionAutoCloseScheduler scheduler;

    @Test
    void closesOnlyAuctionsPastEndsAtAndPublishesLifecycleEventForEach() {
        final Auction dueAuction = auctionRepository.save(
                new Auction("Past Due", null, "seller-1", 10_000L, Instant.now().minusSeconds(5)));
        final Auction openAuction = auctionRepository.save(
                new Auction("Still Open", null, "seller-1", 10_000L, Instant.now().plusSeconds(3600)));

        scheduler.closeExpiredAuctions();

        final Auction reloadedDue = auctionRepository.findById(dueAuction.getId()).orElseThrow();
        assertThat(reloadedDue.getStatus()).isEqualTo(AuctionStatus.CLOSED);
        final Auction reloadedOpen = auctionRepository.findById(openAuction.getId()).orElseThrow();
        assertThat(reloadedOpen.getStatus()).isEqualTo(AuctionStatus.OPEN);

        verify(lifecycleEventPublisher).publish(argThat((AuctionClosedEvent e) ->
                e.auctionId().equals(dueAuction.getId())));
        verify(lifecycleEventPublisher, never()).publish(argThat((AuctionClosedEvent e) ->
                e.auctionId().equals(openAuction.getId())));
    }

    @Test
    void sweepWithNothingDueClosesNothing() {
        final Auction openAuction = auctionRepository.save(
                new Auction("Untouched", null, "seller-1", 10_000L, Instant.now().plusSeconds(3600)));

        scheduler.closeExpiredAuctions();

        final Auction reloaded = auctionRepository.findById(openAuction.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AuctionStatus.OPEN);
    }
}
