package com.shukla.gavel.auction.infrastructure;

import com.shukla.gavel.auction.api.SubscribeRequest;
import com.shukla.gavel.auction.api.UnsubscribeRequest;
import com.shukla.gavel.auction.api.VapidPublicKeyResponse;
import com.shukla.gavel.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(
            final JwtRelayInterceptor jwtRelayInterceptor,
            @Value("${notification.service.url:http://localhost:8083}") final String notificationServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(notificationServiceUrl)
                .requestInterceptors(interceptors -> interceptors.add(jwtRelayInterceptor))
                .build();
    }

    public VapidPublicKeyResponse fetchVapidPublicKey() {
        return restClient.get()
                .uri("/api/v1/notifications/vapid-public-key")
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<VapidPublicKeyResponse>>() {})
                .data();
    }

    public void subscribe(final SubscribeRequest request) {
        restClient.post()
                .uri("/api/v1/notifications/subscriptions")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void unsubscribe(final UnsubscribeRequest request) {
        restClient.method(HttpMethod.DELETE)
                .uri("/api/v1/notifications/subscriptions")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
