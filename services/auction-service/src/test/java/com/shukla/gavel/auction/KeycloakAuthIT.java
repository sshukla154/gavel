package com.shukla.gavel.auction;

import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class KeycloakAuthIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.2")
            .withRealmImportFile("gavel-realm.json");

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> keycloak.getAuthServerUrl() + "/realms/gavel/protocol/openid-connect/certs");
    }

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @LocalServerPort
    int port;

    RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
                               .baseUrl("http://localhost:" + port)
                               .build();
    }

    @Test
    void pingWithoutTokenReturns401() {
        final int status = restClient.get()
                .uri("/api/v1/ping")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(401);
    }

    @Test
    void pingWithBidderTokenReturns200() {
        final String token = obtainToken("bidder", "bidder");
        final int status = restClient.get()
                .uri("/api/v1/ping")
                .header("Authorization", "Bearer " + token)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(200);
    }

    @Test
    void pingWithGuestTokenReturns403() {
        final String token = obtainToken("guest", "guest");
        final int status = restClient.get()
                .uri("/api/v1/ping")
                .header("Authorization", "Bearer " + token)
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(403);
    }

    private String obtainToken(final String username, final String password) {
        final String tokenEndpoint = keycloak.getAuthServerUrl()
                + "/realms/gavel/protocol/openid-connect/token";

        final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", "gavel-test-client");
        formData.add("username", username);
        formData.add("password", password);

        @SuppressWarnings("unchecked")
        final Map<String, Object> response = RestClient.create().post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(Map.class);

        return (String) response.get("access_token");
    }
}
