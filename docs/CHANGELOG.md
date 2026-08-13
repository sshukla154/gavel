# Changelog

All notable changes to Gavel are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions map to phases: `0.x.0` = Phase 0, `1.x.0` = Phase 1, etc.

## [Unreleased]

### Added (0.5 — bid-service + Strimzi Kafka on kind/ArgoCD)
- `helm/bid-service/` — new Helm chart mirroring `helm/auction-service/` (Chart.yaml, values.yaml, values-local.yaml, `_helpers.tpl`, deployment/service/secret/serviceaccount/ingress templates, NOTES.txt); port 8082, own `bids_db` datasource default, `KAFKA_BOOTSTRAP_SERVERS`, no OTel env (bid-service has none)
- `k8s/kafka/kafka-cluster.yaml` — Strimzi `KafkaNodePool` + `Kafka` custom resources, KRaft mode, single combined controller+broker node sized for kind (`apiVersion: kafka.strimzi.io/v1` — verified current against live Strimzi docs, not the older `v1beta2`)
- `k8s/kafka/kafka-topics.yaml` — 8 `KafkaTopic` CRs for the topics from ADR 0010/0012 and their `.DLT`s
- `k8s/argocd/kafka-operator-app.yaml` (sync-wave `-1`), `kafka-app.yaml`, `bid-service-app.yaml` — new ArgoCD Applications, auto-picked-up by the existing `app-of-apps.yaml` directory watch
- Fixed: `helm/auction-service` had no `KAFKA_BOOTSTRAP_SERVERS` or `BID_SERVICE_URL` env vars at all — both silently defaulted to `localhost` inside the pod
- Fixed: the k8s Postgres Application only provisioned `hello_db` — added `primary.initdb.scripts` to also create `bids_db`, mirroring `infra/postgres/init.sql`
- `ci.yml`'s `update-helm-tag` job now bumps both services' chart tags (sequential loop in one job, not a matrix, to avoid two jobs racing to push the same branch)
- `docs/runbooks/local-kubernetes.md` rewritten for both services + Kafka, switched to the single `kubectl apply -f k8s/argocd/app-of-apps.yaml` bootstrap ADR 0006 always intended
- ADR 0013 — Strimzi over Bitnami, single-namespace design, sync-wave ordering, and an explicit note that this was verified statically (`helm lint`/`helm template`/YAML syntax) but not against a live kind cluster (none available in this environment)

### Added (2.4 — Auction closing correctness)
- `AuctionClosedEvent` / `BidRejectedEvent` shared records; new topics `auction.lifecycle.events` and `auction.bids.rejected` (+ matching `.DLT`s), same producer-declares/consumer-DLTs convention as ADR 0010
- Fixed real bug: `AuctionService.placeBid` now rejects bids after `endsAt` even when a scheduler sweep hasn't caught up yet (previously only checked `status`)
- `AuctionAutoCloseScheduler` — sweeps every 5s, closes OPEN auctions past `endsAt` via `SELECT … FOR UPDATE SKIP LOCKED` (multi-replica safe, no new locking dependency), publishes `AuctionClosedEvent` and an SSE `closed` event per closed auction
- Anti-snipe soft-close: a confirmed bid in the final 60s of `endsAt` extends it by 60s (`Auction.isWithinExtensionWindow`/`extend`), pushed live as an SSE `extended` event
- bid-service `auction_state` projection (Flyway V3) fed by `AuctionClosedEvent`; `BidCommandConsumer` now fences — rejects commands for auctions it knows are closed instead of persisting them, publishing `BidRejectedEvent` (reason `AUCTION_CLOSED`)
- auction-service forwards `BidRejectedEvent` onto the live SSE feed as a `rejected` event, so a bidder who loses the closing race gets an explicit answer
- Tests: `AuctionSoftCloseTest`, `AuctionServiceClosingTest` (pure unit), `AuctionAutoCloseSchedulerIT` (Postgres), `BidCommandConsumerIT.rejectsCommandForAuctionAlreadyMarkedClosed` (Kafka+Postgres, proves fencing end to end via a raw consumer on `auction.bids.rejected`)
- ADR 0012 — closing-correctness decision record: DB-locked scheduler over ShedLock, lifecycle topic over a partition-barrier saga, the documented residual cross-topic race and why the existing monotonic price guard bounds its risk

### Added (2.3 — Live auction room)
- `GET /api/v1/auctions/{id}/stream` — SSE live bid feed (auction-service): snapshot on every (re)connect (current price, watcher count, recent bids), then live `bid` events; `watchers` and `heartbeat` events; per-auction connection cap (429 beyond 200 watchers)
- `BidStreamBroadcaster` — per-instance SSE emitter registry with heartbeat eviction of dead connections
- `BidStreamEventListener` — broadcast Kafka consumer (`auction-service-stream-${random.uuid}` groupId, `latest` offset) fanning `BidPlacedEvent` out to connected browsers; every instance sees every event, no sticky sessions
- `GET /api/v1/auctions/{id}/bids` (auction-service) → relays to bid-service `GET /api/v1/bids?auctionId=` (new filter, newest-first, top 50) via the ADR 0009 JWT relay
- Angular auction UI (`ui/src/app/features/auctions/`): auction list with countdowns, create-auction form, and the live auction room — flashing price ticks, "N watching" badge, live bid history, 202 accepted-then-confirmed bid flow, seller close button; SSE consumed via `fetch()` streaming with the Keycloak bearer token (native `EventSource` cannot send headers), auto-reconnect with backoff
- Kafka poison-pill protection in both services: `ErrorHandlingDeserializer` wrapping the Jackson 3 deserializer + `DefaultErrorHandler` with exponential backoff (3 retries) and `DeadLetterPublishingRecoverer`; DLT topics `auction.bids.commands.DLT` / `auction.bids.events.DLT` declared with matching partitions
- `BidCommandConsumerIT.poisonMessageDoesNotBlockThePartition` — proves a malformed message dead-letters instead of stalling the partition
- `BidStreamBroadcasterTest`, `BidStreamControllerTest` — snapshot-on-connect, watcher counting, dead-emitter eviction, degraded mode when bid-service is down
- ADR 0011 — SSE over WebSocket/gRPC for the one-way feed; broadcast consumer groups; snapshot-instead-of-replay; per-instance presence limitation

### Added (2.2 hardening — pipeline correctness)
- `PlaceBidCommand.commandId` (UUID, generated in `AuctionService.placeBid`) — idempotency key for Kafka redelivery
- Flyway `V2__add_command_id_to_bids.sql` in bid-service — `command_id` column with unique constraint
- `Auction.updateCurrentPrice` monotonic guard — rejects non-increasing prices and updates on non-OPEN auctions; redelivered/out-of-order events can no longer lower an established price
- `BidCommandConsumer` restructured: no longer `@Transactional` — bid insert commits before the event publishes (dual-write fix); redelivery reuses the existing row via `findByCommandId` and retries the publish
- `KafkaTopicsConfiguration` in both services — `NewTopic` beans (3 partitions) replace reliance on broker auto-topic-creation
- `EventSerdeTest` — millisecond-fast round-trip guard for both wire records including `Instant` fields
- `AuctionPriceGuardTest` — unit coverage for the monotonic price rules
- `BidCommandConsumerIT.redeliveredCommandDoesNotCreateDuplicateBid` — proves redelivery idempotency end to end
- CI docker + Trivy jobs converted to a service matrix — bid-service image now built, pushed to GHCR, and scanned alongside auction-service
- ADR 0010 — Kafka command/event pipeline decision record (topics, keying, Jackson 3 serde, idempotency, dual-write handling)

### Fixed (2.2 hardening)
- Kafka serde switched from the Jackson 2 `JsonSerializer`/`JsonDeserializer` to Jackson 3 `JacksonJsonSerializer`/`JacksonJsonDeserializer` in all four `application.yaml` files — the Jackson 2 path could not serialize `Instant` (no `jackson-datatype-jsr310` on the classpath), which broke the production publish path in both services
- `maven-failsafe-plugin` goals bound in the root POM — bid-service integration tests were silently never executed in CI
- Test-resource `application.yaml` shadowing removed — renamed to `application-test.yaml` with `@ActiveProfiles("test")` on all test classes, so the main configuration loads in tests and the profile file carries only genuine overrides
- `BidPlacedEventConsumerIT` rewritten to publish through the application's own `KafkaTemplate` (correct serde and type headers by construction)
- `AuctionBiddingIT` correction: uses Testcontainers Postgres with a mocked `BidCommandPublisher` (not Kafka end-to-end, as previously recorded here)

### Added (2.2 — Bidding via Kafka events)
- `PlaceBidCommand` and `BidPlacedEvent` records in `gavel-common` event package — shared between services
- `BidCommandPublisher` in auction-service — publishes to `auction.bids.commands` topic keyed by auction ID
- `BidPlacedEventConsumer` in auction-service — consumes `auction.bids.events`, calls `AuctionService.updateCurrentPrice()`
- `AuctionService.placeBid()` — validates auction is OPEN, publishes `PlaceBidCommand`, returns 202 Accepted
- `AuctionService.updateCurrentPrice()` — updates `current_price_cents` when a bid is confirmed
- `POST /api/v1/auctions/{id}/bids` endpoint (202 Accepted) — fires bid command asynchronously via Kafka
- `Bid` JPA entity + `BidRepository` in bid-service with `findByAuctionId()` finder
- Flyway `V1__create_bids_table.sql` in bid-service — `bids` table with UUID PK and amount check constraint
- `BidCommandConsumer` in bid-service — persists bid to DB then publishes `BidPlacedEvent`
- `BidEventPublisher` in bid-service — publishes to `auction.bids.events` topic keyed by auction ID
- `BidController` in bid-service — now serves real DB data via `BidRepository.findAll()`
- `BidSummary` updated in both services — `id`, `auctionId`, `bidderId`, `amountCents`, `placedAt` (removed `status`)
- `AuctionBiddingIT` — Testcontainers Kafka + Postgres, end-to-end bid placement returns 202
- `BidCommandConsumerIT` — Testcontainers Kafka + Postgres, command → bid persisted in DB
- Kafka added to Docker Compose (Bitnami KRaft mode, port 9092); `infra/postgres/init.sql` mounted to create `bids_db`
- Kafka config wired in `application.yaml` for both services; test overrides disable listener auto-startup

### Added (2.1 — Auction service: create, list, close)
- `Auction` JPA entity with UUID PK — `title`, `description`, `seller_id`, `status` (OPEN/CLOSED), `reserve_price_cents`, `current_price_cents`, `ends_at`, `created_at`
- Flyway `V2__create_auctions_table.sql` — `auctions` table with check constraints and indexes on `status` and `seller_id`
- `AuctionRepository` — `findByStatus(AuctionStatus)` finder
- `AuctionService` — `createAuction`, `listOpenAuctions`, `getAuction`, `closeAuction`; only the seller can close their own auction (403 otherwise)
- `AuctionController` — `POST /api/v1/auctions` (201), `GET /api/v1/auctions`, `GET /api/v1/auctions/{id}`, `POST /api/v1/auctions/{id}/close`; seller identity taken from JWT `sub` claim via `@AuthenticationPrincipal Jwt`
- `AuctionControllerTest` — MockMvc, 401/403/200/201 paths, mocked `AuctionService`
- `AuctionPersistenceIT` — Testcontainers PostgreSQL, full CRUD, forbidden-close, not-found paths

### Added (1.3 — JWT relay for service-to-service calls)
- `bid-service` — new Spring Boot 4 module (port 8082): `GET /api/v1/bids` returns placeholder bids, JWT resource server with same Keycloak realm
- `JwtRelayInterceptor` — `ClientHttpRequestInterceptor` that extracts `AbstractOAuth2TokenAuthenticationToken` from `SecurityContextHolder` and sets `Authorization: Bearer` on outbound requests
- `BidClient` — `RestClient`-based HTTP client in auction-service wired with `JwtRelayInterceptor`; `GET /api/v1/bids` on auction-service relays to bid-service
- `BidRelayIT` — end-to-end integration test: real Keycloak JWT + `MockWebServer` proves the token is relayed verbatim on the outbound call
- `BidControllerTest` in both services — 401/403/200 paths covered with mock JWTs
- `bid-service` added to Docker Compose (port 8082), multi-stage Dockerfile matching auction-service pattern
- CI branch references updated `shukla` → `master` after branch rename
- ADR 0009: JWT relay design and trade-offs

### Added (1.2 — Angular 20 SPA skeleton)
- Angular 20 project scaffolded in `ui/` — `@angular/build:application` (Vite), SCSS, standalone components
- `keycloak-angular@20.1.0` + `keycloak-js@26` wired via `provideKeycloak()` with PKCE authorization code flow
- `gavel-spa` Keycloak client added to `infra/keycloak/gavel-realm.json` (public, PKCE S256, redirects to `localhost:4200`)
- `includeBearerTokenInterceptor` auto-attaches JWT to all `/api/*` requests
- `authGuard` — `createAuthGuard<CanActivateFn>` redirects unauthenticated users to Keycloak login
- `PingComponent` — calls `GET /api/v1/ping`, displays status, service name, total visits; logout button
- Dev proxy (`proxy.conf.json`): `/api` → `http://localhost:8081`; nginx config for Docker serving
- Multi-stage `ui/Dockerfile`: Node 24 build → `nginx:alpine` serve; `gavel-ui` service added to Docker Compose
- `environment.ts` / `environment.prod.ts` — Keycloak connection config
- ADR 0008: Angular 20 SPA with Keycloak OIDC

### Added (1.1 — Keycloak / OAuth2 resource server)
- Keycloak 26 added to Docker Compose on host port 8180 with realm import from `infra/keycloak/gavel-realm.json`
- `gavel-realm.json`: realm `gavel`, role `BIDDER`, users `bidder` (with role) and `guest` (without)
- `spring-boot-starter-oauth2-resource-server` dependency in auction-service
- `SecurityConfig`: stateless JWT resource server, `/actuator/health/**` public, `/api/**` requires `BIDDER` role
- `KeycloakJwtRolesConverter`: extracts `realm_access.roles` claims into `ROLE_*` authorities
- `KeycloakAuthIT`: real end-to-end integration test using `testcontainers-keycloak` — verifies 401, 403, and 200 paths
- `PingControllerTest` updated: `springSecurity()` applied to MockMvc; 401 and 403 cases added alongside the existing 200 case
- Helm chart: `KEYCLOAK_ISSUER_URI` env var wired in `values.yaml`, `values-local.yaml`, and `deployment.yaml`
- ADR 0007: Keycloak as identity provider

### Added (0.4)
- Helm chart `helm/auction-service`: Deployment, Service, Secret, ServiceAccount templates with liveness/readiness probes and resource limits
- kind cluster config `k8s/kind/cluster-config.yaml` — single control-plane node named `gavel`
- Bitnami PostgreSQL Helm values `k8s/postgres/values.yaml`
- ArgoCD Application manifests: `k8s/argocd/auction-service-app.yaml`, `k8s/argocd/postgres-app.yaml`
- CI GitOps loop: `update-helm-tag` job writes new image SHA back to `helm/auction-service/values.yaml` on every push to `shukla` — ArgoCD picks it up automatically
- `application-prod.yaml`: ECS-format JSON structured logging (`logging.structured.format.console: ecs`) active under `prod` Spring profile
- Graceful shutdown (`server.shutdown: graceful`, `timeout-per-shutdown-phase: 30s`) and Kubernetes liveness/readiness actuator probes
- Runbook: `docs/runbooks/local-kubernetes.md`

### Added
- Multi-stage Dockerfile: `deps` → `build` → `extractor` → runtime (Temurin 21 JRE, non-root UID 1001, Spring Boot layered jar)
- GitHub Actions CI workflow (`ci.yml`): build + test (including Testcontainers) → Docker build → GHCR push
- GHCR image `ghcr.io/sshukla154/gavel/auction-service` tagged with branch, short SHA, and `latest` on `shukla`
- `.dockerignore` to strip `target/`, `.idea/`, `.git/`, `infra/`, `docs/` from build context
- CI badge in README
- Explicit `container_name: gavel-*` on all Docker Compose services

### Added (0.2)
- Docker Compose stack: PostgreSQL 16, OTel Collector, Prometheus, Grafana 12, Tempo, Loki
- JPA persistence layer: `Visit` entity, `VisitRepository`, `VisitService`
- Flyway migration `V1__create_visits_table.sql` — `visits` table with BIGSERIAL PK
- `GET /api/v1/ping` now records a visit per call and returns `totalVisits` in the response
- `PingResponse` typed DTO replacing raw `Map<String, String>`
- Modulith-style package structure: `api`, `domain`, `infrastructure` subpackages under `auction`
- `VisitPersistenceIT` — Testcontainers integration test validating Flyway + JPA against real PostgreSQL
- OTel Collector config: OTLP receivers, batch processor, Prometheus/Tempo/Loki exporters
- Grafana auto-provisioned datasources (Prometheus, Tempo, Loki) and auction-service dashboard
- ADR-0003: observability with OTel Collector
- Runbook: local infrastructure operations
- Maven multi-module monorepo: aggregator `pom.xml` with `gavel-common` and `auction-service` modules
- `gavel-common` library: `ApiResponse<T>` response envelope, `ProblemDetails` RFC 7807 error shape
- `auction-service`: Spring Boot 4.0.6 on Java 21, virtual threads enabled, `GET /api/v1/ping` endpoint
- Actuator endpoints: `health`, `info`, `metrics`, `prometheus`
- `PingControllerTest`: `@SpringBootTest` integration test verifying ping response shape
- Repo hygiene: `.gitignore`, `.editorconfig`
- Docs: `README.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`, `FEATURES.md`, `CHANGELOG.md`
- ADR-0001: monorepo structure rationale
- ADR-0002: Spring Boot 4 and Java 21 version pins
