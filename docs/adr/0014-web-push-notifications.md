# 0014 — Web Push outbid notifications

- **Status:** Accepted
- **Date:** 2026-08-13
- **Deciders:** Seemant

## Context

Checkpoint 3.1 (`docs/FEATURES.md`) called for notifications on bid events. The
2026-08-12 enhancement review's highest-scoring version of this proposal was
browser-native Web Push (works with the tab closed, no paid vendor, no email
infrastructure to stand up) rather than email — this ADR covers that specific
scope: outbid alerts only, delivered by a new `notification-service`.

## Decision

**A third Spring Boot service (`notification-service`) consuming `auction.bids.events`
independently, sending VAPID-signed Web Push via `nl.martijndwars:web-push`, reached by
the browser only through auction-service's existing relay pattern.**

| Concern | Decision |
|---|---|
| Trigger | `notification-service` consumes `auction.bids.events` with its own consumer group (`notification-service`) — a third independent subscriber on a topic that already exists. No producer changes; this is the extensibility argument ADR 0010 makes, exercised for real. |
| Projection | A local `highest_bidder` table (auction_id → bidder, amount), upsert-if-higher on each event — the same monotonic-redelivery reasoning as `Auction.updateCurrentPrice` (ADR 0010), so Kafka redelivery/reordering can't regress "who's currently winning" or fire a spurious outbid alert. No explicit locking: `auction.bids.events` is keyed by `auctionId`, so Kafka partition ordering already guarantees one thread processes a given auction's events at a time. |
| Web Push library | `nl.martijndwars:web-push:5.1.2` — verified against Maven Central directly (`repo1.maven.org` metadata), not from memory. GitHub's `5.2.0` tag exists but was never published to Central; using it would have meant depending on a version Maven can't actually resolve. |
| Encoding gotcha | The published 5.1.2 jar's zero-arg `PushService.send(Notification)` defaults to the legacy `AESGCM` content-encoding, which current Chrome/Firefox reject — `VapidPushService` always calls `send(notification, Encoding.AES128GCM)` explicitly. Covered by `VapidPushServiceTest`. |
| Crypto dependency hygiene | web-push's own BouncyCastle dependency (`bcprov-jdk15on:1.70`) is declared `optional=true` and frozen since December 2021 — depend on `bcprov-jdk18on` (current, same `org.bouncycastle.*` packages) explicitly instead. Same reasoning applied to `jose4j`: web-push resolves it at 0.7.9 transitively; pinned to the current 0.9.6 directly. |
| VAPID keypair | Generated once with the library's own BouncyCastle recipe (P-256/`prime256v1` via `KeyPairGenerator.getInstance("ECDH", "BC")`), stored as config (env-var overridable, dev-only defaults committed). **Never regenerated at boot or redeployed casually** — RFC 8292 ties every existing `PushSubscription` to the public key it was created with; rotating the key invalidates every subscriber and forces the SPA to re-subscribe from scratch. |
| Subscription lifecycle | A push response of 404 or 410 (RFC 8030 §5: the push service considers the subscription dead) deletes that `push_subscriptions` row, so a stale endpoint doesn't get retried on every future outbid. |
| Push payload shape | `{"notification": {"title", "body", "data": {"auctionId"}}}` — this is `@angular/service-worker`'s own documented contract for its bundled `ngsw-worker.js` to auto-display a system notification, chosen specifically so the SPA needs **no custom service-worker code** at all. |
| SPA access path | The browser never calls notification-service directly. `proxy.conf.json`/`nginx.conf` only ever routed `/api` to auction-service (bid history already worked this way, ADR 0009); rather than open a second direct browser→backend path, auction-service gained a `NotificationClient` (byte-for-byte mirror of the existing `BidClient`) and a thin `NotificationController` relaying the three endpoints (`GET vapid-public-key`, `POST`/`DELETE subscriptions`). One consistent gateway for the SPA, not two. |
| Jackson | `VapidPushService` builds its own `com.fasterxml.jackson.databind.ObjectMapper` rather than injecting one — Spring Boot 4 is a Jackson 3 (`tools.jackson`) platform and exposes no Jackson 2 `ObjectMapper` bean (the same gotcha hit and documented for Kafka serde in ADR 0010/0012's history). web-push's own types (`Subscription`, `Notification`) are plain Jackson-2-era POJOs with no Jackson 3 support, so this stays intentionally local rather than fighting the platform. |

## Alternatives considered

**Email via MailHog/SMTP** — the review's alternative "Watchlist and outbid
notifications" proposal. Rejected for this checkpoint: more infrastructure (mail
templates, delivery retries), less demo-vivid than a real push landing on a device with
the tab closed, and a separate, lower-scored proposal in the review — kept as a
possible future extension (watchlist, "ending soon" alerts) rather than folded in here.

**Firebase Cloud Messaging (FCM)** — simpler on paper (Google handles delivery), but
adds a third-party account dependency and a proprietary payload format for a portfolio
project whose whole point is demonstrating the underlying protocol (VAPID/RFC 8292),
not a vendor SDK.

**Opening a second direct SPA→notification-service path** (its own proxy/nginx rule) —
rejected in favor of the relay, to keep exactly one browser-facing gateway rather than
two inconsistent access patterns for the app's three backends.

## Consequences

- notification-service has no OTel wiring, matching bid-service's current state — not
  scraped, not traced. Tracked as the same pre-existing observability gap, not new debt.
- The `highest_bidder` projection is a second opinion on "who's winning" alongside
  auction-service's own `current_price_cents` / bid-service's ledger. They agree today
  because all three are fed from the same event stream; a future divergence would be a
  projection bug, not a design ambiguity — worth a reconciliation check if this ever
  matters for money, which it doesn't yet (no payments/settlement exist).
- Web Push delivery is not guaranteed or immediate — the browser's push service is a
  third party (Google's FCM endpoint for Chrome, Mozilla's for Firefox, etc.) outside
  Gavel's control. A dropped push is invisible to the user and to this system; no retry
  beyond what already exists in the Kafka pipeline is implemented (this is a
  notification, not part of the bid pipeline's correctness guarantees).
- `notification-service`'s Helm chart and CI wiring shipped alongside the code in the
  same change, per the review's own explicit warning: skipping platform wiring would
  have replicated the bid-service Helm/CI debt a third time.
