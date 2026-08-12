package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.auction.api.BidSummary;
import com.shukla.gavel.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class BidClient {

    private final RestClient restClient;

    public BidClient(
            final JwtRelayInterceptor jwtRelayInterceptor,
            @Value("${bid.service.url:http://localhost:8082}") final String bidServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(bidServiceUrl)
                .requestInterceptors(interceptors -> interceptors.add(jwtRelayInterceptor))
                .build();
    }

    public List<BidSummary> fetchBids() {
        return restClient.get()
                .uri("/api/v1/bids")
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<BidSummary>>>() {})
                .data();
    }

    public List<BidSummary> fetchBidsForAuction(final UUID auctionId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/bids")
                        .queryParam("auctionId", auctionId)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<BidSummary>>>() {})
                .data();
    }
}
