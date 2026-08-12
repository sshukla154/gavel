package com.shukla.gavel.bid;

import com.shukla.gavel.bid.domain.Bid;
import com.shukla.gavel.bid.domain.BidRepository;
import com.shukla.gavel.bid.infrastructure.BidEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class BidControllerTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    BidEventPublisher bidEventPublisher;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    WebApplicationContext context;

    @Autowired
    BidRepository bidRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                                 .apply(springSecurity())
                                 .build();
    }

    @Test
    void bidsWithBidderRoleReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/bids")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data").isArray())
               .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void bidsFilteredByAuctionIdReturnsNewestFirst() throws Exception {
        final UUID auctionId = UUID.randomUUID();
        final UUID otherAuctionId = UUID.randomUUID();
        bidRepository.save(new Bid(UUID.randomUUID(), auctionId, "bidder-1", 10_000L,
                Instant.parse("2026-01-01T10:00:00Z")));
        bidRepository.save(new Bid(UUID.randomUUID(), auctionId, "bidder-2", 12_000L,
                Instant.parse("2026-01-01T11:00:00Z")));
        bidRepository.save(new Bid(UUID.randomUUID(), otherAuctionId, "bidder-3", 99_000L,
                Instant.parse("2026-01-01T12:00:00Z")));

        mockMvc.perform(get("/api/v1/bids")
                       .param("auctionId", auctionId.toString())
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.length()").value(2))
               .andExpect(jsonPath("$.data[0].bidderId").value("bidder-2"))
               .andExpect(jsonPath("$.data[0].amountCents").value(12000))
               .andExpect(jsonPath("$.data[1].bidderId").value("bidder-1"));
    }

    @Test
    void bidsWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/bids"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void bidsWithNoRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/bids")
                       .with(jwt()))
               .andExpect(status().isForbidden());
    }
}
