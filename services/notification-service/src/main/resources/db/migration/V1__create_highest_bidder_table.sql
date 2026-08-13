-- Per-auction highest-bidder projection, fed by BidPlacedEvent. Exists only to detect
-- "who got outbid" when a new higher bid arrives — not a full mirror of the bid ledger,
-- and not the source of truth for the current price (auction-service is).
CREATE TABLE highest_bidder
(
    auction_id   UUID                     NOT NULL,
    bidder_id    TEXT                     NOT NULL,
    amount_cents BIGINT                   NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_highest_bidder PRIMARY KEY (auction_id)
);
