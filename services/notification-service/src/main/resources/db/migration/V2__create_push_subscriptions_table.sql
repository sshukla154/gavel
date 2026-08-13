-- Web Push subscriptions registered by the Angular SPA's service worker. One row per
-- (bidder, browser/device) pair; endpoint uniquely identifies a subscription per the
-- Push API spec, so re-subscribing the same browser upserts rather than duplicating.
CREATE TABLE push_subscriptions
(
    id         UUID                     NOT NULL DEFAULT gen_random_uuid(),
    bidder_id  TEXT                     NOT NULL,
    endpoint   TEXT                     NOT NULL,
    p256dh     TEXT                     NOT NULL,
    auth_key   TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_push_subscriptions PRIMARY KEY (id),
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_bidder_id ON push_subscriptions (bidder_id);
