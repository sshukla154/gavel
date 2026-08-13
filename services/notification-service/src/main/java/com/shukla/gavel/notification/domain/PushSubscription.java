package com.shukla.gavel.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A Web Push subscription registered by the Angular SPA's service worker. One row per
 * (bidder, browser/device) pair — the endpoint URL is unique per subscription per the
 * Push API spec, so re-subscribing the same browser upserts rather than duplicating.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(nullable = false, unique = true)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(name = "auth_key", nullable = false)
    private String authKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(final String bidderId, final String endpoint,
                            final String p256dh, final String authKey) {
        this.bidderId = bidderId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.authKey = authKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getBidderId() { return bidderId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuthKey() { return authKey; }
    public Instant getCreatedAt() { return createdAt; }
}
