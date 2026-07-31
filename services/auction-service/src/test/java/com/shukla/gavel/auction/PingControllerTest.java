package com.shukla.gavel.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

@SpringBootTest
@Testcontainers
class PingControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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
    void pingWithBidderRoleReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/ping")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.status").value("ok"))
               .andExpect(jsonPath("$.data.service").value("auction-service"))
               .andExpect(jsonPath("$.data.totalVisits").isNumber());
    }

    @Test
    void pingWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void pingWithNoRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/ping")
                       .with(jwt()))
               .andExpect(status().isForbidden());
    }
}
