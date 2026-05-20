# Architecture

## Current state

`auction-service` is the only running service. It serves `GET /api/v1/ping`, persists a `Visit` record per call to PostgreSQL via Flyway-managed schema, and emits traces/metrics/logs via the OpenTelemetry SDK to the local collector stack.

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

| Service | Package | Phase | Responsibility |
|---|---|---|---|
| auction-service | `com.shukla.gavel.auction` | 0+ | Auction lifecycle, bid events, visit tracking |
| identity-service | `com.shukla.gavel.identity` | 1 | Auth via Keycloak integration |
| notification-service | `com.shukla.gavel.notification` | 3 | Email / push on bid events |

## Module structure

```
gavel/
├── pom.xml                  # parent POM (aggregator + dep management)
├── common/                  # gavel-common: shared DTOs, ApiResponse, ProblemDetails
├── infra/                   # Docker Compose infrastructure configs
│   ├── otel-collector/
│   ├── prometheus/
│   ├── grafana/
│   ├── tempo/
│   └── loki/
└── services/
    └── auction-service/     # one folder per service
        └── src/main/java/com/shukla/gavel/auction/
            ├── api/         # controllers and response DTOs
            ├── domain/      # entities, repositories, services
            └── infrastructure/  # framework-specific config
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
