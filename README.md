# Gavel

A real-time auction platform built as a senior-engineer portfolio project.

## Status

| Item | State |
|---|---|
| Phase | 0.2 — observability foundation |
| hello-service | Running on Java 21 / Spring Boot 4.0.6 |
| Database | PostgreSQL 16 via Flyway — wired |
| Observability | OTel Collector → Prometheus / Tempo / Loki / Grafana |
| CI / Docker | Not yet (Phase 0.3) |
| Kubernetes | Not yet (Phase 0.4) |

## Architecture

Maven multi-module monorepo with one service today (`hello-service`) and shared code in `gavel-common`. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system picture and planned services.

## Quickstart

```bash
# Build and run all tests
mvn clean verify
```

```bash
# Start the local infrastructure stack (Postgres, OTel, Prometheus, Grafana, Tempo, Loki)
docker compose up -d
```

```bash
# Start hello-service against the Compose Postgres
mvn -pl services/hello-service -am spring-boot:run
```

```bash
# Hit the endpoint
curl http://localhost:8081/api/v1/ping
```

Grafana is available at [http://localhost:3000](http://localhost:3000) (admin / admin). See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the full local setup guide.

## Tech stack

- Java 21 (virtual threads enabled)
- Spring Boot 4.0.6
- PostgreSQL 16 + Flyway
- OpenTelemetry → Prometheus / Tempo / Loki / Grafana
- Maven multi-module monorepo
- Angular (frontend — Phase 1)
- Kubernetes / Helm / ArgoCD (Phase 0.4)

## Documentation

| Doc | Purpose |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, service map, data flow |
| [DEVELOPMENT.md](docs/DEVELOPMENT.md) | Local setup, build, run, test |
| [FEATURES.md](docs/FEATURES.md) | What's built, in progress, planned |
| [CHANGELOG.md](docs/CHANGELOG.md) | Version history |
| [ADRs](docs/adr/) | Architecture Decision Records |
| [Runbooks](docs/runbooks/) | Operational guides |

<!-- CI badge: added in Checkpoint 0.3 -->
