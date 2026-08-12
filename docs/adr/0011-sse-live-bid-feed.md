# ADR 0011 — SSE for the live bid feed

## Status

Accepted

## Context

Checkpoint 2.3 needs bid activity pushed to browsers in real time: live price ticks, a
scrolling bid feed, and a watcher count on the auction detail page. The events already
flow through Kafka (`auction.bids.events`, ADR 0010); the question is the browser-facing
transport and how each service instance sees every event.

## Decision

**Server-Sent Events over a per-instance broadcast Kafka consumer.**

| Concern | Decision |
|---|---|
| Transport | SSE (`GET /api/v1/auctions/{id}/stream`, `text/event-stream`) — the feed is strictly server→client; WebSocket's bidirectionality buys nothing here, and bids keep flowing through the existing validated REST POST |
| Fan-out | A second `@KafkaListener` on `auction.bids.events` with groupId `auction-service-stream-${random.uuid}` — unique per instance, so every instance receives every event and never competes with the `auction-service` price-updater group |
| Offset policy | `auto.offset.reset=latest` for the stream listener — history is not replayed into the feed; it comes from the snapshot |
| Cold start | On every (re)connect the server sends a `snapshot` event (current price, watcher count, recent bids fetched from bid-service via the ADR 0009 JWT relay), then the live tail. Clients re-render from each snapshot, which makes reconnects self-healing without server-side replay |
| Resume | Bid events carry `id:` (the bidId), but `Last-Event-ID` replay is deliberately NOT implemented — snapshot-on-reconnect covers the gap at much lower complexity. Revisit if feeds ever carry events that a snapshot cannot reconstruct |
| Auth | The endpoint sits behind the normal JWT resource-server chain. Native `EventSource` cannot send an `Authorization` header, so the Angular client streams via `fetch()` + `ReadableStream` with the Keycloak bearer token, refreshing before each (re)connect |
| Presence | Watcher counts are per-instance (in-memory emitter registry). Honest limitation: with multiple replicas each instance reports only its own connections. A shared registry (Redis) is the Phase 4.1 upgrade path |
| Backpressure | Per-auction connection cap (200 → HTTP 429), 30-minute emitter timeout, 15-second heartbeat that evicts dead connections |

Event names on the stream: `snapshot`, `bid`, `watchers`, `heartbeat`.

## Alternatives considered

**WebSocket (STOMP or raw)** — needed only when the client pushes messages over the same
connection. Bids are HTTP POSTs with validation, idempotency and 202 semantics that
already exist; duplicating that path over a socket would weaken it. Presence at scale is
the one thing WebSocket+Redis does better — deferred with the multi-replica presence
question.

**gRPC server streaming** — no browser-native support without grpc-web and a proxy;
wrong cost/benefit for a browser feed.

**Polling** — simplest, but turns the sub-second live-room demo into a 2-5s lag and
wastes the Kafka event stream the platform already pays for.

## Consequences

- Every auction-service instance consumes every bid event (broadcast groups); at this
  scale that is free, and it is what makes any-pod-serves-any-subscriber work without
  sticky sessions.
- Random consumer groups accumulate on the broker as instances restart; groups with no
  members are garbage-collected by Kafka after `offsets.retention.minutes`, so this is
  cosmetic at dev scale.
- nginx-ingress needs `proxy_buffering off` (or the `X-Accel-Buffering: no` header) and
  a read timeout above the heartbeat interval for the k8s path — recorded here so the
  Phase 2 Helm work picks it up.
- The DLT/error-handling work (same checkpoint) protects the stream listener too: a
  poison event goes to `auction.bids.events.DLT` instead of stalling the fan-out.
