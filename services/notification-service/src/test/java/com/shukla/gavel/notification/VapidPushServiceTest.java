package com.shukla.gavel.notification;

import com.shukla.gavel.notification.domain.PushSubscription;
import com.shukla.gavel.notification.domain.PushSubscriptionRepository;
import com.shukla.gavel.notification.infrastructure.VapidPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VapidPushServiceTest {

    // A real, valid uncompressed P-256 point (65 bytes, starts with 0x04) — the
    // Notification constructor decodes p256dh as an actual EC public key, so a
    // placeholder string like "p256dh-key" throws IllegalArgumentException before any
    // mock is ever reached. Reused from a real generated dev VAPID keypair; any valid
    // point works equally well as fake subscriber data.
    private static final String VALID_P256DH =
            "BOXzsDzpB3I1EDxs0kJtuLuOUtsDEzudBEifpr4pVutY4G23xwz8Q1lQ-IQEydGH5jXdkQCmLvRafm_Zp0asHkU";
    private static final String VALID_AUTH = "dGVzdC1hdXRoLXNlY3JldA";

    private final PushService pushService = mock(PushService.class);
    private final PushSubscriptionRepository pushSubscriptionRepository = mock(PushSubscriptionRepository.class);
    private final VapidPushService vapidPushService =
            new VapidPushService(pushService, pushSubscriptionRepository);

    private static PushSubscription subscription() {
        return new PushSubscription("bidder-1", "https://push.example.com/endpoint/abc",
                VALID_P256DH, VALID_AUTH);
    }

    private static HttpResponse responseWithStatus(final int statusCode) {
        final StatusLine statusLine = mock(StatusLine.class);
        given(statusLine.getStatusCode()).willReturn(statusCode);
        final HttpResponse response = mock(HttpResponse.class);
        given(response.getStatusLine()).willReturn(statusLine);
        return response;
    }

    @Test
    void sendsWithAes128GcmEncodingExplicitly() throws Exception {
        // Built and fully stubbed BEFORE the outer given() below starts — nesting a
        // second given()/willReturn() pair inside an unfinished outer one corrupts
        // Mockito's stubbing state for the rest of the test.
        final HttpResponse response = responseWithStatus(201);
        given(pushService.send(any(Notification.class), any(Encoding.class))).willReturn(response);

        vapidPushService.sendOutbidNotification(subscription(), UUID.randomUUID());

        // The library's zero-arg send(Notification) overload defaults to the legacy
        // AESGCM encoding real browsers reject — this must never be called.
        verify(pushService, never()).send(any(Notification.class));
        verify(pushService).send(any(Notification.class), Mockito.eq(Encoding.AES128GCM));
    }

    @Test
    void deletesSubscriptionOn410Gone() throws Exception {
        final PushSubscription subscription = subscription();
        final HttpResponse response = responseWithStatus(410);
        given(pushService.send(any(Notification.class), any(Encoding.class))).willReturn(response);

        vapidPushService.sendOutbidNotification(subscription, UUID.randomUUID());

        verify(pushSubscriptionRepository).deleteByEndpoint(subscription.getEndpoint());
    }

    @Test
    void deletesSubscriptionOn404NotFound() throws Exception {
        final PushSubscription subscription = subscription();
        final HttpResponse response = responseWithStatus(404);
        given(pushService.send(any(Notification.class), any(Encoding.class))).willReturn(response);

        vapidPushService.sendOutbidNotification(subscription, UUID.randomUUID());

        verify(pushSubscriptionRepository).deleteByEndpoint(subscription.getEndpoint());
    }

    @Test
    void doesNotDeleteSubscriptionOnSuccess() throws Exception {
        final HttpResponse response = responseWithStatus(201);
        given(pushService.send(any(Notification.class), any(Encoding.class))).willReturn(response);

        vapidPushService.sendOutbidNotification(subscription(), UUID.randomUUID());

        verify(pushSubscriptionRepository, never()).deleteByEndpoint(any());
    }

    @Test
    void swallowsSendFailureWithoutThrowing() throws Exception {
        given(pushService.send(any(Notification.class), any(Encoding.class)))
                .willThrow(new java.io.IOException("push service unreachable"));

        vapidPushService.sendOutbidNotification(subscription(), UUID.randomUUID());

        verify(pushSubscriptionRepository, never()).deleteByEndpoint(any());
    }
}
