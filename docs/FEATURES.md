# Features

## Phase 0 — Walking skeleton

| Feature | Status | Checkpoint |
|---|---|---|
| Maven monorepo + auction-service skeleton | ✅ Done | 0.1 |
| PostgreSQL + Flyway + observability stack | ✅ Done | 0.2 |
| Multi-stage Docker + CI to GHCR | ✅ Done | 0.3 |
| kind + Helm + ArgoCD GitOps | ✅ Done | 0.4 |
| bid-service Helm chart + Strimzi Kafka on kind/ArgoCD | ✅ Done (unverified live — see ADR 0013) | 0.5 |

## Phase 1 — Identity + Angular shell

| Feature | Status | Checkpoint |
|---|---|---|
| Keycloak integration | ✅ Done | 1.1 |
| Angular project skeleton | ✅ Done | 1.2 |
| JWT propagation to services | ✅ Done | 1.3 |

## Phase 2 — Auction core

| Feature | Status | Checkpoint |
|---|---|---|
| Auction service (create, list, close) | ✅ Done | 2.1 |
| Bidding via Kafka events | ✅ Done | 2.2 |
| Live auction room: SSE bid feed, auction UI, presence, DLT hardening | ✅ Done | 2.3 |
| Auction closing correctness: scheduler at endsAt, soft-close, fencing | ✅ Done | 2.4 |

## Phase 3 — Notifications + search

| Feature | Status | Checkpoint |
|---|---|---|
| Web Push outbid notifications (notification-service, VAPID) | ✅ Done | 3.1 |
| OpenSearch auction catalog | 📋 Planned | 3.2 |

## Phase 4 — Polish + public release

| Feature | Status | Checkpoint |
|---|---|---|
| Redis caching layer | 📋 Planned | 4.1 |
| Rate limiting + API gateway | 📋 Planned | 4.2 |
| Load testing + perf tuning | 📋 Planned | 4.3 |
| Public repo hygiene (LICENSE, CONTRIBUTING) | 📋 Planned | 4.4 |
