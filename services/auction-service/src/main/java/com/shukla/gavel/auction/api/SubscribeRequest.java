package com.shukla.gavel.auction.api;

/**
 * Mirrors the browser's PushSubscription.toJSON() shape exactly:
 * {@code {endpoint, keys: {p256dh, auth}}}. Relayed verbatim to notification-service.
 */
public record SubscribeRequest(String endpoint, Keys keys) {

    public record Keys(String p256dh, String auth) {
    }
}
