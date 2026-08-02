# ADR 0010 — Kafka command/event pipeline for bidding

## Status

Accepted

## Context

Placing a bid spans two services: auction-service owns the auction and its current price,
bid-service owns the bid ledger. A synchronous call would couple auction-service's write path
to bid-service's availability and give no natural spot for replay or audit. Bidding is also
the highest-write-volume operation in the system, and Phase 2.3 (real-time bid feed) needs a
stream to subscribe to.

## Decision

**Asynchronous command/event flow over Kafka, one topic per direction.**

```
auction-service ──PlaceBidCommand──▶ auction.bids.commands ──▶ bid-service
      ▲                                                             │ persists Bid
      └────BidPlacedEvent◀── auction.bids.events ◀─────────────────┘
        (updates current price)
```

| Concern | Decision |
|---|---|
| Topics | `auction.bids.commands` (auction→bid), `auction.bids.events` (bid→auction); each declared as a `NewTopic` bean by its producer (3 partitions, RF 1 in dev) |
| Partitioning key | `auctionId` — all messages for one auction stay ordered on one partition |
| Wire format | JSON via spring-kafka's Jackson 3 serde (`JacksonJsonSerializer` / `JacksonJsonDeserializer`); records shared from `gavel-common` |
| Delivery semantics | At-least-once; duplicates handled at the consumers, not prevented at the broker |
| Idempotency | `PlaceBidCommand.commandId` (UUID, generated at origin) with a unique constraint on `bids.command_id` — a redelivered command reuses the existing row |
| Dual-write | Listener is not transactional: bid insert commits first, event publishes after. A publish failure throws → offset not committed → redelivery → idempotent lookup reuses the row and retries the publish |
| Price integrity | `Auction.updateCurrentPrice` is monotonic — rejects non-increasing prices and updates on closed auctions, so redelivered/out-of-order events cannot lower an established price |
| HTTP contract | `POST /api/v1/auctions/{id}/bids` returns 202 Accepted — the bid is recorded asynchronously |

### Jackson 3, explicitly

Spring Boot 4 is a Jackson 3 platform (`tools.jackson`). The Jackson 2 serde
(`JsonSerializer`) only supports `java.time` when `jackson-datatype-jsr310` is on the
classpath — which it is not here, and both wire records carry `Instant`. The Jackson 2
classes are also deprecated in spring-kafka 4. Messaging therefore uses the Jackson 3 serde,
which handles `java.time` natively with zero extra dependencies. A fast unit test
(`EventSerdeTest`) round-trips both records so a serde regression fails in milliseconds
instead of as a container-test timeout.

## Alternatives considered

**Synchronous REST call to bid-service** — simple, but couples availability, loses ordering
and replay, and offers no stream for the Phase 2.3 real-time feed.

**Transactional outbox** — closes the remaining at-least-once gap properly (event row and bid
row in one transaction, relay publishes). Deliberately deferred: the idempotent-redelivery
design gives equivalent guarantees for this flow at far less machinery. Revisit if a consumer
appears for which redelivery is not tolerable.

**Kafka transactions (exactly-once)** — requires transactional producers on every hop and
still doesn't cover the DB write. Overkill for this scale.

## Consequences

- Bids are eventually consistent: a 202 does not guarantee the bid is queryable yet.
- `spring.kafka.listener.auto-startup: false` in the test profile keeps non-Kafka tests fast;
  Kafka ITs re-enable it via `@DynamicPropertySource`. A new listener must have an IT that
  does this, or it ships untested.
- bid-service accepts commands without checking auction state; a bid against a just-closed
  auction is persisted but its event is rejected by the monotonic/OPEN guard on the auction
  side. Fencing at bid-service is future work.
- No bid-service Helm chart / k8s Kafka yet — the documented kind/ArgoCD path cannot deploy
  Phase 2 (tracked as Phase 2 debt).
