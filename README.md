# Gavel

A real-time auction platform built as a senior-engineer portfolio project.

## Status

| Item | State |
|---|---|
| Phase | 0.4 — kind + Helm + ArgoCD GitOps |
| auction-service | Running on Java 21 / Spring Boot 4.0.6 |
| Database | PostgreSQL 16 via Flyway — wired |
| Observability | OTel Collector → Prometheus / Tempo / Loki / Grafana |
| CI / Docker | GitHub Actions → GHCR (`ghcr.io/sshukla154/gavel/auction-service`) |
| Kubernetes | kind cluster + Helm chart + ArgoCD GitOps |

## Architecture

Maven multi-module monorepo with one service today (`auction-service`) and shared code in `gavel-common`. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system picture and planned services.

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
# Start auction-service against the Compose Postgres
mvn -pl services/auction-service -am spring-boot:run
```

```bash
# Hit the endpoint
curl http://localhost:8081/api/v1/ping
```

Grafana is available at [http://localhost:3000](http://localhost:3000) (admin / admin). See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the full local setup guide.

## Container image

```bash
# Pull the latest image (built from the shukla branch)
docker pull ghcr.io/sshukla154/gavel/auction-service:latest

# Run against an existing Postgres instance
docker run --rm \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/hello_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -p 8081:8081 \
  ghcr.io/sshukla154/gavel/auction-service:latest
```

The image runs as UID 1001 and exposes port 8081. Flyway migrations run on startup; a Postgres instance must be reachable. The Actuator health endpoint at `/actuator/health` is wired as the Docker HEALTHCHECK.

## Tech stack

- Java 21 (virtual threads enabled)
- Spring Boot 4.0.6
- PostgreSQL 16 + Flyway
- OpenTelemetry → Prometheus / Tempo / Loki / Grafana
- Maven multi-module monorepo
- Angular (frontend — Phase 1)
- Kubernetes / Helm / ArgoCD (Phase 0.4 — done)

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

![CI](https://github.com/sshukla154/gavel/actions/workflows/ci.yml/badge.svg?branch=shukla)
