-- Local projection of auction lifecycle, fed by AuctionClosedEvent. Absence of a row
-- means the auction is still open as far as this service knows; a row here is
-- authoritative only for fencing new commands, not a full mirror of auction-service.
CREATE TABLE auction_state
(
    auction_id UUID                     NOT NULL,
    status     TEXT                     NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_auction_state PRIMARY KEY (auction_id)
);
