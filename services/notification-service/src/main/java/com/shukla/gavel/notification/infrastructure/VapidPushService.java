package com.shukla.gavel.notification.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shukla.gavel.notification.domain.PushSubscription;
import com.shukla.gavel.notification.domain.PushSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Sends VAPID-signed Web Push notifications via nl.martijndwars:web-push. The VAPID
 * keypair (wired into the injected PushService by WebPushConfiguration) is generated
 * once and never rotated — see docs/adr/0014; regenerating it would invalidate every
 * existing subscription (RFC 8292).
 */
@Slf4j
@Component
public class VapidPushService {

    private final PushService pushService;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    // Spring Boot 4 is a Jackson 3 (tools.jackson) platform — it does not expose a
    // Jackson 2 ObjectMapper bean. web-push's own types are plain Jackson-2-era POJOs
    // with no Jackson 3 support, so this stays a locally-built Jackson 2 instance rather
    // than an injected bean.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VapidPushService(final PushService pushService,
                            final PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushService = pushService;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    /**
     * Sends an outbid alert. Any failure is logged and swallowed — a lost push
     * notification is not worth failing the Kafka listener over. A 404/410 response
     * means the push service considers the subscription dead (RFC 8030 §5); that row is
     * deleted so future bids on other auctions don't keep retrying a stale endpoint.
     */
    public void sendOutbidNotification(final PushSubscription subscription, final UUID auctionId) {
        try {
            final String payload = objectMapper.writeValueAsString(Map.of(
                    "title", "You've been outbid",
                    "body", "Someone placed a higher bid on an auction you're watching.",
                    "auctionId", auctionId.toString()));
            final Subscription.Keys keys =
                    new Subscription.Keys(subscription.getP256dh(), subscription.getAuthKey());
            final Notification notification =
                    new Notification(new Subscription(subscription.getEndpoint(), keys), payload);

            // The zero-arg send(Notification) overload in the published 5.1.2 jar
            // defaults to the legacy AESGCM encoding, which current browsers reject —
            // always pass AES128GCM explicitly.
            final HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
            final int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                log.debug("Push subscription expired, deleting: endpoint={}", subscription.getEndpoint());
                pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
            } else if (status >= 300) {
                log.warn("Push send failed: status={} endpoint={}", status, subscription.getEndpoint());
            }
        } catch (final GeneralSecurityException | IOException | JoseException | ExecutionException pushFailure) {
            log.warn("Push send error: endpoint={}", subscription.getEndpoint(), pushFailure);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Push send interrupted: endpoint={}", subscription.getEndpoint());
        }
    }
}
