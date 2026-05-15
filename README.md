# Gavel

A real-time auction platform built as a senior-engineer portfolio project.

## Status

| Item | State |
|---|---|
| Phase | 0.1 — walking skeleton |
| hello-service | Running on Java 21 / Spring Boot 4.0.6 |
| Database | Not yet wired (Phase 0.2) |
| Observability | Not yet wired (Phase 0.2) |
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
# Start hello-service locally
mvn -pl services/hello-service -am spring-boot:run
```

```bash
# In a second terminal
curl http://localhost:8081/api/v1/ping
```

```bash
# In a second terminal
curl http://localhost:8081/actuator/health
```

## Tech stack

- Java 21 (virtual threads enabled)
- Spring Boot 4.0.6
- Maven multi-module monorepo
- Angular (frontend — Phase 1)
- PostgreSQL 16 / Kafka / Redis / OpenSearch (Phase 2+)
- Kubernetes / Helm / ArgoCD (Phase 0.4)

## Documentation

| Doc | Purpose |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, service map, data flow |
| [DEVELOPMENT.md](docs/DEVELOPMENT.md) | Local setup, build, run, test |
| [FEATURES.md](docs/FEATURES.md) | What's built, in progress, planned |
| [CHANGELOG.md](docs/CHANGELOG.md) | Version history |
| [ADRs](docs/adr/) | Architecture Decision Records |

<!-- CI badge: added in Checkpoint 0.3 -->
