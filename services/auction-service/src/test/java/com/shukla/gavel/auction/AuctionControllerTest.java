package com.shukla.gavel.auction;

import com.shukla.gavel.auction.api.AuctionResponse;
import com.shukla.gavel.auction.api.PlaceBidResponse;
import com.shukla.gavel.auction.domain.AuctionService;
import com.shukla.gavel.auction.domain.AuctionStatus;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class AuctionControllerTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    AuctionService auctionService;

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    static final UUID AUCTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static final AuctionResponse SAMPLE = new AuctionResponse(
            AUCTION_ID,
            "Vintage Watch",
            "A rare 1960s timepiece",
            "seller-sub-001",
            AuctionStatus.OPEN,
            50_000L,
            50_000L,
            Instant.parse("2027-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z")
    );

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                                 .apply(springSecurity())
                                 .build();
    }

    @Test
    void createAuctionReturnsCreated() throws Exception {
        given(auctionService.createAuction(any(), anyString())).willReturn(SAMPLE);

        mockMvc.perform(post("/api/v1/auctions")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("""
                               {
                                 "title": "Vintage Watch",
                                 "reservePriceCents": 50000,
                                 "endsAt": "2027-01-01T00:00:00Z"
                               }
                               """)
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.data.title").value("Vintage Watch"))
               .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void createAuctionWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auctions")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"title\":\"x\",\"reservePriceCents\":1,\"endsAt\":\"2027-01-01T00:00:00Z\"}"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void listAuctionsReturnsOpenAuctions() throws Exception {
        given(auctionService.listOpenAuctions()).willReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/v1/auctions")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data[0].title").value("Vintage Watch"))
               .andExpect(jsonPath("$.data[0].status").value("OPEN"));
    }

    @Test
    void getAuctionByIdReturnsAuction() throws Exception {
        given(auctionService.getAuction(SAMPLE.id())).willReturn(SAMPLE);

        mockMvc.perform(get("/api/v1/auctions/" + SAMPLE.id())
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.id").value(SAMPLE.id().toString()));
    }

    @Test
    void closeAuctionReturnsClosedAuction() throws Exception {
        final AuctionResponse closed = new AuctionResponse(
                SAMPLE.id(), SAMPLE.title(), SAMPLE.description(), SAMPLE.sellerId(),
                AuctionStatus.CLOSED, SAMPLE.reservePriceCents(), SAMPLE.currentPriceCents(),
                SAMPLE.endsAt(), SAMPLE.createdAt());
        given(auctionService.closeAuction(any(), anyString())).willReturn(closed);

        mockMvc.perform(post("/api/v1/auctions/" + SAMPLE.id() + "/close")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void placeBidReturnsAccepted() throws Exception {
        final PlaceBidResponse bidResponse = new PlaceBidResponse(AUCTION_ID, "bidder-sub", 60_000L);
        given(auctionService.placeBid(any(), anyString(), any())).willReturn(bidResponse);

        mockMvc.perform(post("/api/v1/auctions/" + AUCTION_ID + "/bids")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"amountCents\":60000}")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isAccepted())
               .andExpect(jsonPath("$.data.amountCents").value(60000));
    }

    @Test
    void auctionEndpointsWithNoRoleReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/auctions").with(jwt()))
               .andExpect(status().isForbidden());
    }
}
