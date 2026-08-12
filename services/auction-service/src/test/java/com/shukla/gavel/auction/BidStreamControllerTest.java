package com.shukla.gavel.auction;

import com.shukla.gavel.auction.api.AuctionResponse;
import com.shukla.gavel.auction.api.BidSummary;
import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.auction.domain.AuctionStatus;
import com.shukla.gavel.auction.infrastructure.BidClient;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class BidStreamControllerTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final UUID AUCTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @MockitoBean
    AuctionService auctionService;

    @MockitoBean
    BidClient bidClient;

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        given(auctionService.getAuction(AUCTION_ID)).willReturn(new AuctionResponse(
                AUCTION_ID, "Test Auction", null, "seller-1", AuctionStatus.OPEN,
                100_000L, 120_000L, Instant.now().plusSeconds(3600), Instant.now()));
        given(bidClient.fetchBidsForAuction(AUCTION_ID)).willReturn(List.of(
                new BidSummary(UUID.randomUUID().toString(), AUCTION_ID.toString(),
                        "bidder-1", 120_000L, Instant.parse("2026-01-01T00:00:00Z"))));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                                 .apply(springSecurity())
                                 .build();
    }

    @Test
    void streamSendsSnapshotOnConnect() throws Exception {
        // Fresh auction id: the broadcaster is a context-wide singleton, so reusing an id
        // across tests would accumulate watchers and break the count assertion.
        final UUID auctionId = UUID.randomUUID();
        given(auctionService.getAuction(auctionId)).willReturn(new AuctionResponse(
                auctionId, "Snapshot Auction", null, "seller-1", AuctionStatus.OPEN,
                100_000L, 120_000L, Instant.now().plusSeconds(3600), Instant.now()));
        given(bidClient.fetchBidsForAuction(auctionId)).willReturn(List.of(
                new BidSummary(UUID.randomUUID().toString(), auctionId.toString(),
                        "bidder-1", 120_000L, Instant.parse("2026-01-01T00:00:00Z"))));

        final MvcResult result = mockMvc.perform(get("/api/v1/auctions/{id}/stream", auctionId)
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(request().asyncStarted())
               .andReturn();

        final String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:snapshot");
        assertThat(body).contains("\"currentPriceCents\":120000");
        assertThat(body).contains("\"watchers\":1");
        assertThat(body).contains("bidder-1");
    }

    @Test
    void streamForUnknownAuctionReturnsNotFound() throws Exception {
        final UUID unknownId = UUID.randomUUID();
        given(auctionService.getAuction(unknownId))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        mockMvc.perform(get("/api/v1/auctions/{id}/stream", unknownId)
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isNotFound());
    }

    @Test
    void streamWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/{id}/stream", AUCTION_ID))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void bidHistoryRelaysBidServiceData() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/{id}/bids", AUCTION_ID)
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data[0].bidderId").value("bidder-1"))
               .andExpect(jsonPath("$.data[0].amountCents").value(120000));
    }

    @Test
    void streamOpensEvenWhenBidServiceIsDown() throws Exception {
        final UUID auctionId = UUID.randomUUID();
        given(auctionService.getAuction(auctionId)).willReturn(new AuctionResponse(
                auctionId, "Degraded Auction", null, "seller-1", AuctionStatus.OPEN,
                100_000L, 120_000L, Instant.now().plusSeconds(3600), Instant.now()));
        given(bidClient.fetchBidsForAuction(auctionId))
                .willThrow(new RuntimeException("bid-service unavailable"));

        final MvcResult result = mockMvc.perform(get("/api/v1/auctions/{id}/stream", auctionId)
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("event:snapshot")
                .contains("\"recentBids\":[]");
    }
}
