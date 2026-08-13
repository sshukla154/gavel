# Gavel

A real-time auction platform built as a senior-engineer portfolio project.

## Status

| Item | State |
|---|---|
| Phase | 2.2 — bidding via Kafka events (done); next: 2.3 real-time bid feed |
| Services | auction-service (8081) + bid-service (8082), Java 21 / Spring Boot 4.0.7 |
| Frontend | Angular SPA with Keycloak login (4200) |
| Auth | Keycloak 26 (`gavel` realm), JWT resource servers + service-to-service JWT relay |
| Messaging | Kafka (KRaft) — `auction.bids.commands` / `auction.bids.events` |
| Database | PostgreSQL 16 via Flyway — `hello_db` (auction) + `bids_db` (bid) |
| Observability | OTel Collector → Prometheus / Tempo / Loki / Grafana |
| CI / Docker | GitHub Actions → GHCR (auction-service + bid-service images, Trivy-scanned) |
| Kubernetes | kind cluster + Helm charts (both services) + Strimzi Kafka + ArgoCD GitOps (manifests statically verified, not yet run against a live cluster — see ADR 0013) |

## Architecture

Maven multi-module monorepo: `auction-service` owns auctions and the current price, `bid-service` owns the bid ledger, shared event records and DTOs live in `gavel-common`. Bidding is asynchronous — a `POST /api/v1/auctions/{id}/bids` returns 202 and the bid flows through Kafka. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the system picture and [ADR 0010](docs/adr/0010-kafka-bidding-pipeline.md) for the messaging design.

## Quickstart

```bash
# Build and run all tests (integration tests need Docker)
mvn clean verify
```

```bash
# Start the local infrastructure stack (Postgres, Kafka, Keycloak, bid-service, UI, observability)
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
```

Both images run as UID 1001 (auction-service on 8081, bid-service on 8082). Flyway migrations run on startup; Postgres and Kafka must be reachable. The Actuator health endpoint at `/actuator/health` is wired as the Docker HEALTHCHECK.

## Tech stack

- Java 21 (virtual threads enabled)
- Spring Boot 4.0.7
- Kafka (Jackson 3 JSON serde, idempotent consumers)
- PostgreSQL 16 + Flyway
- Keycloak 26 (OAuth2 / OIDC)
- Angular SPA
- OpenTelemetry → Prometheus / Tempo / Loki / Grafana
- Maven multi-module monorepo
- Kubernetes / Helm / ArgoCD

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
