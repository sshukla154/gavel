# ADR 0012 — Auction closing correctness

## Status

Accepted

## Context

Two real bugs existed at checkpoint 2.3: `placeBid` checked `status` but never `endsAt`,
so a bid landed and was accepted indefinitely until a seller manually closed the
auction; and `closeAuction` mutated the row without publishing anything, so bid-service
had no way to learn an auction had closed and kept accepting `PlaceBidCommand` for it
forever (`bid-service accepts commands without checking auction state` — ADR 0010's
admitted future work).

## Decision

**A DB-locked scheduler auto-closes at `endsAt`, an anti-snipe soft-close extends it
under contest, and a lifecycle-event projection lets bid-service fence commands for
auctions it knows are closed — with the residual cross-topic race window accepted and
documented rather than solved with a saga.**

| Concern | Decision |
|---|---|
| Immediate bug fix | `AuctionService.placeBid` now also rejects when `Auction.hasEnded(now)`, independent of whether the scheduler has caught up yet |
| Auto-close | `AuctionAutoCloseScheduler` sweeps every 5s; `AuctionRepository.lockOpenAuctionsEndingBy` uses `SELECT … FOR UPDATE SKIP LOCKED` so multiple auction-service replicas never double-close the same row — no new locking dependency (ShedLock, etc.) |
| Anti-snipe soft-close | A confirmed bid landing in the final 60s of `endsAt` extends it by 60s (`Auction.isWithinExtensionWindow` / `extend`); pushed live as an `extended` SSE event so the countdown visibly jumps for every watcher |
| Lifecycle propagation | New topic `auction.lifecycle.events` (keyed by auctionId): auction-service publishes `AuctionClosedEvent` after the closing transaction commits — from `closeAuction` and from the scheduler sweep, both outside the DB transaction to keep the established commit-then-publish pattern (ADR 0010) |
| Fencing | bid-service projects `AuctionClosedEvent` into a small `auction_state` table (`AuctionLifecycleEventConsumer`). `BidCommandConsumer` checks it before persisting; a command for a closed auction is not persisted and a `BidRejectedEvent` (reason `AUCTION_CLOSED`) is published instead |
| Feedback loop | auction-service consumes `auction.bids.rejected` and forwards it onto the live SSE feed (`rejected` event) — a bidder who loses the closing race gets an explicit answer instead of an indefinite "awaiting confirmation" |
| DLT coverage | The new topics follow the existing pattern exactly: each service declares a `NewTopic` for what it produces and a `.DLT` for what it consumes, picked up by the `KafkaErrorHandlingConfiguration` bean already wired in 2.3 |

## Alternatives considered

**Partition-barrier saga** (publish a `CLOSING` marker onto `auction.bids.commands`
itself, keyed by auctionId, so partition ordering fences in-flight bids with no race
window). Rejected for this pass: it requires either a multi-type listener dispatch
(class-level `@KafkaListener` + `@KafkaHandler`, relying on JacksonJsonSerializer's type
header) or a wrapper envelope type, plus a `CLOSING → CLOSED` state machine with a
timeout/compensation path if bid-service never confirms. Genuinely stronger — zero race
window — but the judged review scored it lower on feasibility than the lifecycle-topic
version for the same reason: more moving parts, harder to test given Kafka ITs only run
in CI on this dev machine. Revisit if the residual race below ever causes a real problem.

**Transactional outbox** for the lifecycle event. Same reasoning as ADR 0010: revisit
if a consumer appears for which redelivery/at-least-once is not tolerable. The current
consumer (bid-service's fencing check) tolerates a missed or delayed `AuctionClosedEvent`
because of the backstop described below — not free of risk, but the risk is bounded and
documented rather than requiring new infrastructure.

## Consequences

- **Documented residual race**: `auction.lifecycle.events` and `auction.bids.commands`
  are different topics with no cross-topic ordering guarantee. A bid sent immediately
  before close can still be persisted in bid-service's ledger if `AuctionClosedEvent`
  hasn't propagated yet.
- **Why that's low-risk**: `Auction.updateCurrentPrice` (ADR 0010's monotonic guard)
  already refuses to raise the price on a CLOSED auction. A bid that slips past the
  bid-service fence is persisted in the bid-service ledger but never moves the price and
  never reaches the live feed as a confirmed bid — auction-service's own state is the
  final word on what counts.
- The scheduler publishes `AuctionClosedEvent` and broadcasts the SSE `closed` event
  from the same non-transactional loop as the DB commit; a crash between commit and
  publish loses that specific notification (the auction is still correctly CLOSED in the
  database — only the notification is at risk, and a later manual `closeAuction` call
  can't recover it since the row is already CLOSED). Acceptable at this scale; the fix
  is the outbox pattern above if it ever matters.
- Every `@KafkaListener` this ADR adds follows the ADR 0010 rule: it must ship with an IT
  that re-enables `auto-startup`, or it ships untested. `AuctionLifecycleEventConsumer`
  and `BidRejectedEventConsumer` both are — see `BidCommandConsumerIT.rejectsCommandForAuctionAlreadyMarkedClosed`.
