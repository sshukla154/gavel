package com.shukla.gavel.auction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class AuctionBiddingIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"))
            .withKraft();

    @DynamicPropertySource
    static void infrastructure(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    WebApplicationContext context;

    @Test
    void placeBidOnOpenAuctionReturnsAccepted() throws Exception {
        final MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                                               .apply(springSecurity())
                                               .build();

        final String createBody = """
                {
                  "title": "Rare Painting",
                  "reservePriceCents": 100000,
                  "endsAt": "2027-12-31T23:59:59Z"
                }
                """;

        final String auctionId = mockMvc.perform(post("/api/v1/auctions")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(createBody)
                       .with(jwt().jwt(j -> j.subject("seller-1"))
                                  .authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isCreated())
               .andReturn()
               .getResponse()
               .getContentAsString()
               .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/auctions/" + auctionId + "/bids")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"amountCents\":120000}")
                       .with(jwt().jwt(j -> j.subject("bidder-1"))
                                  .authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isAccepted())
               .andExpect(jsonPath("$.data.auctionId").value(auctionId))
               .andExpect(jsonPath("$.data.amountCents").value(120000));
    }
}
