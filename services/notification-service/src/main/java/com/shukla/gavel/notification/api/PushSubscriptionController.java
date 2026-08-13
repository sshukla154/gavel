package com.shukla.gavel.notification.api;

import com.shukla.gavel.common.api.ApiResponse;
import com.shukla.gavel.notification.domain.PushSubscription;
import com.shukla.gavel.notification.domain.PushSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
public class PushSubscriptionController {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final String vapidPublicKey;

    public PushSubscriptionController(
            final PushSubscriptionRepository pushSubscriptionRepository,
            @Value("${gavel.notification.vapid.public-key}") final String vapidPublicKey) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.vapidPublicKey = vapidPublicKey;
    }

    @GetMapping("/vapid-public-key")
    public ApiResponse<VapidPublicKeyResponse> vapidPublicKey() {
        return ApiResponse.of(new VapidPublicKeyResponse(vapidPublicKey));
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@RequestBody final SubscribeRequest request,
                          @AuthenticationPrincipal final Jwt jwt) {
        final String bidderId = jwt.getSubject();
        pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing -> log.debug("Subscription already registered: endpoint={}", request.endpoint()),
                        () -> {
                            pushSubscriptionRepository.save(new PushSubscription(
                                    bidderId, request.endpoint(),
                                    request.keys().p256dh(), request.keys().auth()));
                            log.debug("Registered push subscription: bidder={}", bidderId);
                        });
    }

    @DeleteMapping("/subscriptions")
    public void unsubscribe(@RequestBody final UnsubscribeRequest request) {
        pushSubscriptionRepository.deleteByEndpoint(request.endpoint());
        log.debug("Removed push subscription: endpoint={}", request.endpoint());
    }
}
