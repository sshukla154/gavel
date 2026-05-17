# 0003 — Observability with OTel Collector

- **Status:** Accepted
- **Date:** 2026-05-17
- **Deciders:** Seemant

## Context

The project needs traces, metrics, and logs visible in a local development environment and exportable to any cloud-managed backend (Azure Monitor, Datadog, Grafana Cloud) without code changes. We need a single decision that works from laptop to production.

## Decision

Use the **OpenTelemetry Collector (contrib distribution)** as the single telemetry ingestion point. Services emit OTLP to the collector; the collector fans out to:

- **Tempo** — distributed trace storage
- **Prometheus** — metrics (collector exposes a scrape endpoint; Prometheus pulls from it)
- **Loki** — log aggregation
- **Grafana** — unified dashboards across all three backends

Spring Boot 4's `spring-boot-starter-opentelemetry` instruments the application automatically. The collector is the only component that knows where data goes — swapping backends requires only a collector config change, not a code change.

## Consequences

**Easier:**
- Swapping Tempo for Jaeger, or Loki for Elasticsearch, requires only a collector config change.
- Adding a new service means only a new scrape target in Prometheus — no per-service configuration.
- Grafana's exemplar linking (trace IDs embedded in metrics) works out of the box via the Prometheus → Tempo datasource link.

**Harder:**
- One more process to operate locally. The Compose stack has six containers.
- OTel Collector config has its own learning curve (pipelines, processors, exporters).
- The contrib image is large (~200 MB). The core image is smaller but lacks the Loki exporter.

## Alternatives considered

**Direct SDK exporters per backend (no collector)**
Each service would need separate exporters for Prometheus, Jaeger/Zipkin, and Loki. Rejected: code changes required when switching backends; exporter configuration scattered across services.

**ELK stack (Elasticsearch + Logstash + Kibana)**
Mature, but heavy. Elasticsearch needs 2 GB+ of RAM locally, which is impractical for a developer laptop alongside Docker Desktop. Rejected on resource grounds.

**Datadog / Grafana Cloud (managed)**
Excellent for production, but requires an account and API key for local development. The goal is a fully local, zero-dependency dev stack. Rejected for Phase 0; may revisit in Phase 5.
