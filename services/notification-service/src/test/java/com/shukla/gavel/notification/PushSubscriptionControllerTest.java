package com.shukla.gavel.notification;

import com.shukla.gavel.notification.domain.PushSubscription;
import com.shukla.gavel.notification.domain.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class PushSubscriptionControllerTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    PushSubscriptionRepository pushSubscriptionRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pushSubscriptionRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                                 .apply(springSecurity())
                                 .build();
    }

    @Test
    void vapidPublicKeyWithBidderRoleReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/vapid-public-key")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.publicKey").isNotEmpty());
    }

    @Test
    void subscribePersistsSubscriptionForCurrentBidder() throws Exception {
        final String body = """
                {"endpoint":"https://push.example.com/abc","keys":{"p256dh":"p-key","auth":"a-key"}}""";

        mockMvc.perform(post("/api/v1/notifications/subscriptions")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER")).jwt(j -> j.subject("bidder-42")))
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(body))
               .andExpect(status().isCreated());

        final PushSubscription saved = pushSubscriptionRepository.findByEndpoint("https://push.example.com/abc")
                .orElseThrow();
        assertThat(saved.getBidderId()).isEqualTo("bidder-42");
        assertThat(saved.getP256dh()).isEqualTo("p-key");
    }

    @Test
    void subscribeTwiceWithSameEndpointDoesNotDuplicate() throws Exception {
        final String body = """
                {"endpoint":"https://push.example.com/dup","keys":{"p256dh":"p-key","auth":"a-key"}}""";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/notifications/subscriptions")
                           .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER")))
                           .contentType(MediaType.APPLICATION_JSON)
                           .content(body))
                   .andExpect(status().isCreated());
        }

        assertThat(pushSubscriptionRepository.findAll())
                .filteredOn(s -> s.getEndpoint().equals("https://push.example.com/dup"))
                .hasSize(1);
    }

    @Test
    void unsubscribeRemovesSubscription() throws Exception {
        pushSubscriptionRepository.save(new PushSubscription(
                "bidder-1", "https://push.example.com/remove-me", "p-key", "a-key"));

        mockMvc.perform(delete("/api/v1/notifications/subscriptions")
                       .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BIDDER")))
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("""
                               {"endpoint":"https://push.example.com/remove-me"}"""))
               .andExpect(status().isOk());

        assertThat(pushSubscriptionRepository.findByEndpoint("https://push.example.com/remove-me")).isEmpty();
    }

    @Test
    void vapidPublicKeyWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/vapid-public-key"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void vapidPublicKeyWithNoRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/vapid-public-key")
                       .with(jwt()))
               .andExpect(status().isForbidden());
    }
}
