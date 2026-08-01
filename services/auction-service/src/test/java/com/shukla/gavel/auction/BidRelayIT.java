package com.shukla.gavel.auction;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "bid.service.url=http://localhost:19082"
)
@Testcontainers
@WireMockTest(httpPort = 19082)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "WireMock Jetty NIO requires loopback pipe which fails on some Windows environments; runs in CI on Linux")
class BidRelayIT {

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
    void bidsEndpointRelaysJwtToBidService(final WireMockRuntimeInfo wireMock) {
        stubFor(get(urlEqualTo("/api/v1/bids"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":[],"timestamp":"2024-01-01T00:00:00Z"}
                                """)));

        final String bidderToken = obtainToken("bidder", "bidder");

        final int statusCode = restClient.get()
                .uri("/api/v1/bids")
                .header("Authorization", "Bearer " + bidderToken)
                .exchange((req, res) -> res.getStatusCode().value());

        assertThat(statusCode).isEqualTo(200);
        verify(getRequestedFor(urlEqualTo("/api/v1/bids"))
                .withHeader("Authorization", equalTo("Bearer " + bidderToken)));
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
