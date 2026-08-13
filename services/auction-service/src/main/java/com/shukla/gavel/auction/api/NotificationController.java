package com.shukla.gavel.auction.api;

import com.shukla.gavel.auction.infrastructure.NotificationClient;
import com.shukla.gavel.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The SPA only ever talks to auction-service (see ADR 0009's JWT-relay pattern, already
 * used for bid history); this relays push-subscription calls to notification-service the
 * same way BidClient relays to bid-service, rather than opening a second direct browser
 * path to a third backend.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationClient notificationClient;

    public NotificationController(final NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @GetMapping("/vapid-public-key")
    public ApiResponse<VapidPublicKeyResponse> vapidPublicKey() {
        return ApiResponse.of(notificationClient.fetchVapidPublicKey());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@RequestBody final SubscribeRequest request) {
        notificationClient.subscribe(request);
    }

    @DeleteMapping("/subscriptions")
    public void unsubscribe(@RequestBody final UnsubscribeRequest request) {
        notificationClient.unsubscribe(request);
    }
}
