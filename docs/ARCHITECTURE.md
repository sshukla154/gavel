# Architecture

## Current state

Only `hello-service` exists. It serves a single `/api/v1/ping` endpoint, uses Spring Boot 4.0.6 on Java 21 with virtual threads, and exposes Actuator for health/metrics. No database, no messaging, no observability stack yet.

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
                        │  │  hello-service   │  │  auction-svc   │  │  ..  │ │
                        │  │  (Phase 0)       │  │  (Phase 2)     │  │      │ │
                        │  └────────┬─────────┘  └───────┬────────┘  └──────┘ │
                        │           │                     │                    │
                        │           ▼                     ▼                    │
                        │  ┌──────────────────────────────────────────────┐   │
                        │  │  PostgreSQL  │  Kafka  │  Redis  │ OpenSearch │   │
                        │  │  (Phase 0.2) │ (Ph. 2) │ (Ph. 2) │  (Ph. 3)  │   │
                        │  └──────────────────────────────────────────────┘   │
                        └─────────────────────────────────────────────────────┘

                        ┌─────────────────────────────────────────────────────┐
                        │  Observability (Phase 0.2)                           │
                        │  OTel Collector → Prometheus / Tempo / Loki          │
                        │  Grafana dashboards                                  │
                        └─────────────────────────────────────────────────────┘
```

## Services

| Service | Package | Phase | Responsibility |
|---|---|---|---|
| hello-service | `com.shukla.gavel.hello` | 0 | Walking skeleton; proves the pipeline |
| auction-service | `com.shukla.gavel.auction` | 2 | Auction lifecycle, bid events |
| identity-service | `com.shukla.gavel.identity` | 1 | Auth via Keycloak integration |
| notification-service | `com.shukla.gavel.notification` | 3 | Email / push on bid events |

## Module structure

```
gavel/
├── pom.xml                  # parent POM (aggregator + dep management)
├── common/                  # gavel-common: shared DTOs, ApiResponse, ProblemDetails
└── services/
    └── hello-service/       # one folder per service
```

## API conventions

- All REST endpoints: `/api/v1/...`
- Success: `ApiResponse<T>` envelope (from `gavel-common`)
- Errors: RFC 7807 `ProblemDetails` (from `gavel-common`)

## Observability

Planned for Phase 0.2: each service emits traces/metrics/logs via the OpenTelemetry SDK to a local OTel Collector, which fans out to:
- **Prometheus** (metrics scraping)
- **Tempo** (trace storage)
- **Loki** (log aggregation)
- **Grafana** (unified dashboards)
