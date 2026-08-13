# Gavel

A real-time auction platform built as a senior-engineer portfolio project.

## Status

| Item | State |
|---|---|
| Phase | 3.1 — Web Push outbid notifications (done); next: 3.2 OpenSearch catalog |
| Services | auction-service (8081) + bid-service (8082) + notification-service (8083), Java 21 / Spring Boot 4.0.7 |
| Frontend | Angular SPA with Keycloak login (4200), live auction room, Web Push opt-in |
| Auth | Keycloak 26 (`gavel` realm), JWT resource servers + service-to-service JWT relay |
| Messaging | Kafka (KRaft) — bid commands/events, auction lifecycle events, bid rejections |
| Database | PostgreSQL 16 via Flyway — `hello_db` (auction) + `bids_db` (bid) + `notifications_db` (notification) |
| Observability | OTel Collector → Prometheus / Tempo / Loki / Grafana (auction-service only) |
| CI / Docker | GitHub Actions → GHCR (all three service images, Trivy-scanned) |
| Kubernetes | kind cluster + Helm charts (all three services) + Strimzi Kafka + ArgoCD GitOps (manifests statically verified, not yet run against a live cluster — see ADR 0013) |

## Architecture

Maven multi-module monorepo: `auction-service` owns auctions, the current price, and the live SSE bid feed; `bid-service` owns the bid ledger and fences bids for closed auctions; `notification-service` sends Web Push alerts when a bidder is outbid. Shared event records and DTOs live in `gavel-common`. Bidding is asynchronous — a `POST /api/v1/auctions/{id}/bids` returns 202 and the bid flows through Kafka. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the system picture, [ADR 0010](docs/adr/0010-kafka-bidding-pipeline.md) for the messaging design, and [ADR 0014](docs/adr/0014-web-push-notifications.md) for Web Push.

## Quickstart

```bash
# Build and run all tests (integration tests need Docker)
mvn clean verify
```

```bash
# Start the local infrastructure stack (Postgres, Kafka, Keycloak, bid-service, notification-service, UI, observability)
docker compose up -d
```

```bash
# Start auction-service against the Compose infrastructure
mvn -pl services/auction-service -am spring-boot:run
```

Grafana is available at [http://localhost:3000](http://localhost:3000) (admin / admin). See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the full local setup guide and [docs/OPERATIONS.md](docs/OPERATIONS.md) for run modes.

## Container images

```bash
docker pull ghcr.io/sshukla154/gavel/auction-service:latest
docker pull ghcr.io/sshukla154/gavel/bid-service:latest
docker pull ghcr.io/sshukla154/gavel/notification-service:latest
```

All three images run as UID 1001 (ports 8081/8082/8083 respectively). Flyway migrations run on startup; Postgres and Kafka must be reachable. The Actuator health endpoint at `/actuator/health` is wired as the Docker HEALTHCHECK.

## Tech stack

- Java 21 (virtual threads enabled)
- Spring Boot 4.0.7
- Kafka (Jackson 3 JSON serde, idempotent consumers)
- PostgreSQL 16 + Flyway
- Keycloak 26 (OAuth2 / OIDC)
- Web Push / VAPID (`nl.martijndwars:web-push`)
- Angular SPA (`@angular/service-worker` for Push)
- OpenTelemetry → Prometheus / Tempo / Loki / Grafana
- Maven multi-module monorepo
- Kubernetes / Helm / ArgoCD / Strimzi

## Documentation

| Doc | Purpose |
|---|---|
| [OPERATIONS.md](docs/OPERATIONS.md) | Installation, configuration, start/stop/verify for all three modes |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, service map, data flow |
| [DEVELOPMENT.md](docs/DEVELOPMENT.md) | Local setup, build, run, test |
| [FEATURES.md](docs/FEATURES.md) | What's built, in progress, planned |
| [CHANGELOG.md](docs/CHANGELOG.md) | Version history |
| [ADRs](docs/adr/) | Architecture Decision Records |
| [Runbooks](docs/runbooks/) | Operational guides |

![CI](https://github.com/sshukla154/gavel/actions/workflows/ci.yml/badge.svg?branch=master)
