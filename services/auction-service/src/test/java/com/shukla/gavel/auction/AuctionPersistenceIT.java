package com.shukla.gavel.auction;

import com.shukla.gavel.auction.api.AuctionResponse;
import com.shukla.gavel.auction.api.CreateAuctionRequest;
import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.auction.domain.AuctionStatus;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AuctionPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @Autowired
    AuctionService auctionService;

    static final Instant ENDS_AT = Instant.parse("2027-06-01T00:00:00Z");

    @Test
    void createAndRetrieveAuction() {
        final CreateAuctionRequest request = new CreateAuctionRequest(
                "Vintage Clock", "19th century mantel clock", 75_000L, ENDS_AT);

        final AuctionResponse created = auctionService.createAuction(request, "seller-001");

        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Vintage Clock");
        assertThat(created.sellerId()).isEqualTo("seller-001");
        assertThat(created.status()).isEqualTo(AuctionStatus.OPEN);
        assertThat(created.reservePriceCents()).isEqualTo(75_000L);
        assertThat(created.currentPriceCents()).isEqualTo(75_000L);

        final AuctionResponse fetched = auctionService.getAuction(created.id());
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.title()).isEqualTo("Vintage Clock");
    }

    @Test
    void listOpenAuctionsExcludesClosedOnes() {
        final CreateAuctionRequest openRequest = new CreateAuctionRequest(
                "Open Auction", null, 10_000L, ENDS_AT);
        final CreateAuctionRequest toCloseRequest = new CreateAuctionRequest(
                "Soon Closed", null, 20_000L, ENDS_AT);

        auctionService.createAuction(openRequest, "seller-002");
        final AuctionResponse toClose = auctionService.createAuction(toCloseRequest, "seller-002");
        auctionService.closeAuction(toClose.id(), "seller-002");

        final List<AuctionResponse> openList = auctionService.listOpenAuctions();

        assertThat(openList).noneMatch(a -> a.id().equals(toClose.id()));
        assertThat(openList).noneMatch(a -> a.status() == AuctionStatus.CLOSED);
    }

    @Test
    void closeAuctionBySeller() {
        final CreateAuctionRequest request = new CreateAuctionRequest(
                "Seller Close Test", null, 5_000L, ENDS_AT);
        final AuctionResponse auction = auctionService.createAuction(request, "seller-003");

        final AuctionResponse closed = auctionService.closeAuction(auction.id(), "seller-003");

        assertThat(closed.status()).isEqualTo(AuctionStatus.CLOSED);
    }

    @Test
    void closeAuctionByNonSellerThrowsForbidden() {
        final CreateAuctionRequest request = new CreateAuctionRequest(
                "Auth Test Auction", null, 5_000L, ENDS_AT);
        final AuctionResponse auction = auctionService.createAuction(request, "seller-004");

        assertThatThrownBy(() -> auctionService.closeAuction(auction.id(), "intruder-999"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getUnknownAuctionThrowsNotFound() {
        final java.util.UUID unknown = java.util.UUID.randomUUID();

        assertThatThrownBy(() -> auctionService.getAuction(unknown))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
