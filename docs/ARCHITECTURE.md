# Architecture

## Current state (Phase 3.1)

Three running Spring Boot services plus an Angular SPA. `auction-service` (port 8081) owns the auction lifecycle, current price, and the live SSE bid feed; `bid-service` (port 8082) owns the bid ledger and consumes bid commands from Kafka; `notification-service` (port 8083) sends Web Push "you've been outbid" alerts, consuming `auction.bids.events` with its own consumer group. All three are OAuth2 resource servers against the shared Keycloak realm (`gavel`), with JWT relay on service-to-service calls (ADR 0009) — the SPA talks only to auction-service, which relays to bid-service and notification-service server-side. Bidding is fully asynchronous over Kafka with idempotent consumers and monotonic price guards (ADR 0010), closing correctness is enforced by an auto-close scheduler and bid-service fencing (ADR 0012), and both bid-service and notification-service have their own Helm chart plus an in-cluster Strimzi Kafka on the kind/ArgoCD path (ADR 0013). `auction-service` emits traces/metrics/logs via the OpenTelemetry SDK to the collector stack; `bid-service` and `notification-service` have no OTel wiring yet — neither is scraped or traced.

## Bidding flow (Kafka)

```
  POST /api/v1/auctions/{id}/bids  (202 Accepted)
        │
        ▼
  auction-service ──PlaceBidCommand──▶ auction.bids.commands ──▶ bid-service
        ▲                                                            │ persists Bid
        └───updates current price◀── auction.bids.events ◀──────────┘ (BidPlacedEvent)
```

Both topics are keyed by `auctionId` (per-auction ordering) and declared as `NewTopic` beans by their producing service. Delivery is at-least-once: `PlaceBidCommand.commandId` plus a unique constraint on `bids.command_id` dedupes redelivery, and `Auction.updateCurrentPrice` is monotonic so stale events cannot lower a price. Wire format is JSON via spring-kafka's Jackson 3 serde. See ADR 0010 for the full decision record.

## Notifications (Web Push)

```
  auction.bids.events ──▶ notification-service ──▶ (VAPID Web Push) ──▶ browser
                              │ highest_bidder projection
                              │ (upsert-if-higher, per auctionId)
                              ▼
                    push_subscriptions (per bidder)
```

`notification-service` consumes the same `auction.bids.events` topic auction-service's
price-updater and SSE broadcast listeners already consume — a third independent
consumer group, no changes to any producer. It keeps a local highest-bidder-per-auction
projection; when a new bid outbids a different bidder, every Web Push subscription
registered for that bidder gets a VAPID-signed push. The SPA never calls
notification-service directly — subscription register/unregister and the VAPID public
key both go through auction-service's relay (`NotificationClient`, mirroring the
existing `BidClient` relay pattern from ADR 0009), the same way bid history does. See
ADR 0014 for the Web Push library choice and the projection's idempotency design.

## Target system

```
                        ┌─────────────────────────────────────────────────────┐
                        │  Kubernetes cluster (kind locally, AKS in prod)      │
                        │                                                       │
  Browser / CLI         │  ┌──────────────┐    ┌──────────────────────────┐   │
      │                 │  │  Angular SPA │    │   API Gateway / Ingress   │   │
      └─────────────────┼─▶│  (Phase 1)   │───▶│   (nginx-ingress)        │   │
                        │  └──────────────┘    └────────────┬─────────────┘   │
                        │                                    │                  │
                        │              ┌─────────────────────┼────────────┐    │
                        │              ▼                     ▼            ▼    │
                        │  ┌──────────────────┐  ┌────────────────┐  ┌──────┐ │
                        │  │ auction-service  │  │  bid-service   │  │  ..  │ │
                        │  │  (Phase 0+)      │  │  (Phase 2)     │  │      │ │
                        │  └────────┬─────────┘  └───────┬────────┘  └──────┘ │
                        │           │                     │                    │
                        │           ▼                     ▼                    │
                        │  ┌──────────────────────────────────────────────┐   │
                        │  │  PostgreSQL  │  Kafka  │  Redis  │ OpenSearch │   │
                        │  │  (Phase 0.2) │ (Ph. 2) │ (Ph. 2) │  (Ph. 3)  │   │
                        │  └──────────────────────────────────────────────┘   │
                        └─────────────────────────────────────────────────────┘
```

## Services

| Service | Package | Phase | Status | Responsibility |
|---|---|---|---|---|
| auction-service | `com.shukla.gavel.auction` | 0+ | Running (8081) | Auction lifecycle, current price, live SSE bid feed, bid command origin, publishes `PlaceBidCommand`, relays to bid-service/notification-service |
| bid-service | `com.shukla.gavel.bid` | 2.1+ | Running (8082) | Bid ledger: consumes `PlaceBidCommand`, persists bids, publishes `BidPlacedEvent` back to auction-service, fences commands for closed auctions |
| notification-service | `com.shukla.gavel.notification` | 3.1 | Running (8083) | Consumes `BidPlacedEvent`, maintains a highest-bidder-per-auction projection, sends VAPID Web Push "you've been outbid" alerts |
| UI (Angular SPA) | `src/app/` | 1.2+ | Running (4200) | Browser-based client; authenticates via Keycloak, displays auctions and bid feeds, service-worker Web Push opt-in |

Identity is provided by Keycloak directly (ADR 0007) — there is no separate identity-service module.

## Module structure

```
gavel/
├── pom.xml                  # parent POM (aggregator + dep management)
├── common/                  # gavel-common: shared DTOs, event records, ApiResponse, ProblemDetails
├── ui/                      # Angular SPA (Keycloak login, port 4200)
├── infra/                   # Docker Compose infrastructure configs
│   ├── keycloak/            # realm import (gavel realm)
│   ├── postgres/            # init.sql — creates bids_db
│   ├── otel-collector/
│   ├── prometheus/
│   ├── grafana/
│   ├── tempo/
│   └── loki/
└── services/                # one folder per service, same package layout in each
    ├── auction-service/
    │   └── src/main/java/com/shukla/gavel/auction/
    │       ├── api/         # controllers and response DTOs
    │       ├── domain/      # entities, repositories, services
    │       └── infrastructure/  # Kafka publishers/consumers, security, topics
    ├── bid-service/
    │   └── src/main/java/com/shukla/gavel/bid/
    │       ├── api/
    │       ├── domain/
    │       └── infrastructure/
    └── notification-service/
        └── src/main/java/com/shukla/gavel/notification/
            ├── api/         # push-subscription REST endpoints
            ├── domain/      # HighestBidder projection, PushSubscription
            └── infrastructure/  # Kafka consumer, VAPID/web-push wiring
```

## API conventions

- All REST endpoints: `/api/v1/...`
- Success: `ApiResponse<T>` envelope (from `gavel-common`)
- Errors: RFC 7807 `ProblemDetails` (from `gavel-common`)

## Observability

Each service emits telemetry via the OpenTelemetry SDK. The OTel Collector acts as the single ingestion point and fans out to the appropriate backend.

```
  auction-service (OTLP HTTP :4318)
        │
        ▼
  OTel Collector
  ├── Traces  ──▶  Tempo  (trace storage, query via Grafana Explore)
  ├── Metrics ──▶  Prometheus  (scrape :8889, query via Grafana dashboards)
  └── Logs    ──▶  Loki  (log aggregation, query via Grafana Explore)
                        │
                        ▼
                    Grafana  (unified dashboards at localhost:3000)
```

Prometheus also scrapes `auction-service` directly at `/actuator/prometheus` (`host.docker.internal:8081`) for JVM and HTTP metrics.

The Grafana `auction-service` dashboard shows request rate, p99 latency, JVM heap usage, and total visit count.
