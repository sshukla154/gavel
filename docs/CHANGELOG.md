# Changelog

All notable changes to Gavel are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions map to phases: `0.x.0` = Phase 0, `1.x.0` = Phase 1, etc.

## [Unreleased]

### Added (0.4)
- Helm chart `helm/hello-service`: Deployment, Service, Secret, ServiceAccount templates with liveness/readiness probes and resource limits
- kind cluster config `k8s/kind/cluster-config.yaml` — single control-plane node named `gavel`
- Bitnami PostgreSQL Helm values `k8s/postgres/values.yaml`
- ArgoCD Application manifests: `k8s/argocd/hello-service-app.yaml`, `k8s/argocd/postgres-app.yaml`
- CI GitOps loop: `update-helm-tag` job writes new image SHA back to `helm/hello-service/values.yaml` on every push to `shukla` — ArgoCD picks it up automatically
- `application-prod.yaml`: ECS-format JSON structured logging (`logging.structured.format.console: ecs`) active under `prod` Spring profile
- Graceful shutdown (`server.shutdown: graceful`, `timeout-per-shutdown-phase: 30s`) and Kubernetes liveness/readiness actuator probes
- Runbook: `docs/runbooks/local-kubernetes.md`

### Added
- Multi-stage Dockerfile: `deps` → `build` → `extractor` → runtime (Temurin 21 JRE, non-root UID 1001, Spring Boot layered jar)
- GitHub Actions CI workflow (`ci.yml`): build + test (including Testcontainers) → Docker build → GHCR push
- GHCR image `ghcr.io/sshukla154/gavel/hello-service` tagged with branch, short SHA, and `latest` on `shukla`
- `.dockerignore` to strip `target/`, `.idea/`, `.git/`, `infra/`, `docs/` from build context
- CI badge in README
- Explicit `container_name: gavel-*` on all Docker Compose services

### Added (0.2)
- Docker Compose stack: PostgreSQL 16, OTel Collector, Prometheus, Grafana 12, Tempo, Loki
- JPA persistence layer: `Visit` entity, `VisitRepository`, `VisitService`
- Flyway migration `V1__create_visits_table.sql` — `visits` table with BIGSERIAL PK
- `GET /api/v1/ping` now records a visit per call and returns `totalVisits` in the response
- `PingResponse` typed DTO replacing raw `Map<String, String>`
- Modulith-style package structure: `api`, `domain`, `infrastructure` subpackages under `hello`
- `VisitPersistenceIT` — Testcontainers integration test validating Flyway + JPA against real PostgreSQL
- OTel Collector config: OTLP receivers, batch processor, Prometheus/Tempo/Loki exporters
- Grafana auto-provisioned datasources (Prometheus, Tempo, Loki) and hello-service dashboard
- ADR-0003: observability with OTel Collector
- Runbook: local infrastructure operations
- Maven multi-module monorepo: aggregator `pom.xml` with `gavel-common` and `hello-service` modules
- `gavel-common` library: `ApiResponse<T>` response envelope, `ProblemDetails` RFC 7807 error shape
- `hello-service`: Spring Boot 4.0.6 on Java 21, virtual threads enabled, `GET /api/v1/ping` endpoint
- Actuator endpoints: `health`, `info`, `metrics`, `prometheus`
- `PingControllerTest`: `@SpringBootTest` integration test verifying ping response shape
- Repo hygiene: `.gitignore`, `.editorconfig`
- Docs: `README.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`, `FEATURES.md`, `CHANGELOG.md`
- ADR-0001: monorepo structure rationale
- ADR-0002: Spring Boot 4 and Java 21 version pins
