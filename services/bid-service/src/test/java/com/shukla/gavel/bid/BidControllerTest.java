package com.shukla.gavel.bid;

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

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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
