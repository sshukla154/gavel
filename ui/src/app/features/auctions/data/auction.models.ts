export type AuctionStatus = 'OPEN' | 'CLOSED';

/** Standard response envelope used by all Gavel REST endpoints. */
export interface ApiEnvelope<T> {
  data: T;
  timestamp: string;
}

export interface AuctionResponse {
  id: string;
  title: string;
  description: string | null;
  sellerId: string;
  status: AuctionStatus;
  reservePriceCents: number;
  currentPriceCents: number;
  endsAt: string;
  createdAt: string;
}

export interface BidSummary {
  id: string;
  auctionId: string;
  bidderId: string;
  amountCents: number;
  placedAt: string;
}

export interface PlaceBidRequest {
  amountCents: number;
}

/** Payload of the 202 Accepted response to a bid submission. */
export interface BidAccepted {
  auctionId: string;
  bidderId: string;
  amountCents: number;
}

export interface CreateAuctionRequest {
  title: string;
  description: string | null;
  reservePriceCents: number;
  endsAt: string;
}

/** RFC 7807 problem details returned by the backend on errors. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

// --- SSE event payloads (GET /api/v1/auctions/{id}/stream) ---

export interface AuctionSnapshotEvent {
  auctionId: string;
  currentPriceCents: number;
  watchers: number;
  recentBids: BidSummary[];
}

export interface BidEvent {
  bidId: string;
  auctionId: string;
  bidderId: string;
  amountCents: number;
  placedAt: string;
}

export interface WatchersEvent {
  count: number;
}

/** endsAt was pushed out by the anti-snipe soft-close (checkpoint 2.4). */
export interface ExtendedEvent {
  auctionId: string;
  endsAt: string;
}

/** The auction transitioned to CLOSED — via seller close or the auto-close scheduler. */
export interface ClosedEvent {
  auctionId: string;
}

/** A bid was fenced out instead of persisted — currently only reason 'AUCTION_CLOSED'. */
export interface RejectedEvent {
  commandId: string;
  auctionId: string;
  bidderId: string;
  amountCents: number;
  reason: string;
}

export type AuctionStreamEvent =
  | { type: 'snapshot'; payload: AuctionSnapshotEvent }
  | { type: 'bid'; payload: BidEvent }
  | { type: 'watchers'; payload: WatchersEvent }
  | { type: 'extended'; payload: ExtendedEvent }
  | { type: 'closed'; payload: ClosedEvent }
  | { type: 'rejected'; payload: RejectedEvent };
