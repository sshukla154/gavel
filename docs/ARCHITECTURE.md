# Architecture

## Current state (Phase 2.2)

Two running Spring Boot services plus an Angular SPA. `auction-service` (port 8081) owns the auction lifecycle and current price; `bid-service` (port 8082) owns the bid ledger and consumes bid commands from Kafka. Both are OAuth2 resource servers against the shared Keycloak realm (`gavel`), with JWT relay on service-to-service calls (ADR 0009). Bidding is fully asynchronous over Kafka with idempotent consumers and monotonic price guards (ADR 0010). `auction-service` emits traces/metrics/logs via the OpenTelemetry SDK to the collector stack; `bid-service` has no OTel wiring yet (no SDK dependency, no OTLP/Prometheus config) — it is not scraped or traced.

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
| auction-service | `com.shukla.gavel.auction` | 0+ | Running (8081) | Auction lifecycle, current price, bid command origin, visit tracking, publishes `PlaceBidCommand` |
| bid-service | `com.shukla.gavel.bid` | 2.1+ | Running (8082) | Bid ledger: consumes `PlaceBidCommand`, persists bids, publishes `BidPlacedEvent` back to auction-service |
| notification-service | `com.shukla.gavel.notification` | 3 | Planned | Email / push on bid events |
| UI (Angular SPA) | `src/app/` | 1.2+ | Running (4200) | Browser-based client; authenticates via Keycloak, displays auctions and bid feeds |

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
    └── bid-service/
        └── src/main/java/com/shukla/gavel/bid/
            ├── api/
            ├── domain/
            └── infrastructure/
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
