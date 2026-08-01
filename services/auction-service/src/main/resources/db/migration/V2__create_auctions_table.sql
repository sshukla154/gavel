CREATE TABLE auctions
(
    id                  UUID                     NOT NULL DEFAULT gen_random_uuid(),
    title               TEXT                     NOT NULL,
    description         TEXT,
    seller_id           TEXT                     NOT NULL,
    status              TEXT                     NOT NULL DEFAULT 'OPEN',
    reserve_price_cents BIGINT                   NOT NULL,
    current_price_cents BIGINT                   NOT NULL,
    ends_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_auctions PRIMARY KEY (id),
    CONSTRAINT chk_auctions_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_auctions_prices CHECK (current_price_cents >= 0 AND reserve_price_cents > 0)
);

CREATE INDEX idx_auctions_status ON auctions (status);
CREATE INDEX idx_auctions_seller_id ON auctions (seller_id);
