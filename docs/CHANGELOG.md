# Changelog

All notable changes to Gavel are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions map to phases: `0.x.0` = Phase 0, `1.x.0` = Phase 1, etc.

## [Unreleased]

### Added
- Maven multi-module monorepo: aggregator `pom.xml` with `gavel-common` and `hello-service` modules
- `gavel-common` library: `ApiResponse<T>` response envelope, `ProblemDetails` RFC 7807 error shape
- `hello-service`: Spring Boot 4.0.6 on Java 21, virtual threads enabled, `GET /api/v1/ping` endpoint
- Actuator endpoints: `health`, `info`, `metrics`, `prometheus`
- `PingControllerTest`: `@SpringBootTest` integration test verifying ping response shape
- Repo hygiene: `.gitignore`, `.editorconfig`
- Docs: `README.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`, `FEATURES.md`, `CHANGELOG.md`
- ADR-0001: monorepo structure rationale
- ADR-0002: Spring Boot 4 and Java 21 version pins
