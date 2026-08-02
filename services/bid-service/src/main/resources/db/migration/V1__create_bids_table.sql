CREATE TABLE bids
(
    id           UUID                     NOT NULL DEFAULT gen_random_uuid(),
    auction_id   UUID                     NOT NULL,
    bidder_id    TEXT                     NOT NULL,
    amount_cents BIGINT                   NOT NULL,
    placed_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_bids PRIMARY KEY (id),
    CONSTRAINT chk_bids_amount CHECK (amount_cents > 0)
);

CREATE INDEX idx_bids_auction_id ON bids (auction_id);
CREATE INDEX idx_bids_bidder_id ON bids (bidder_id);
