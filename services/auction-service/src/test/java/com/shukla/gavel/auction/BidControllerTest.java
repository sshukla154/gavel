package com.shukla.gavel.auction;

import com.shukla.gavel.auction.api.BidSummary;
import com.shukla.gavel.auction.infrastructure.BidClient;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class BidControllerTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    BidClient bidClient;

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        given(bidClient.fetchBids()).willReturn(List.of(
                new BidSummary(
                        UUID.randomUUID().toString(),
                        "00000000-0000-0000-0000-000000000001",
                        "bidder-1",
                        10000L,
                        Instant.parse("2026-01-01T00:00:00Z"))
        ));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                                 .apply(springSecurity())
                                 .build();
    }

    @Test
    void bidsWithBidderRoleReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/bids")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data[0].auctionId").value("00000000-0000-0000-0000-000000000001"))
               .andExpect(jsonPath("$.data[0].amountCents").value(10000));
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
