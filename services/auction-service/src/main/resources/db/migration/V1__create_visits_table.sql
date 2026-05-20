-- Tracks each call to GET /api/v1/ping.
-- Used by the walking skeleton to prove end-to-end persistence.
CREATE TABLE visits
(
    id          BIGSERIAL                  NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_visits PRIMARY KEY (id)
);
