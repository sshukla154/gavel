-- Idempotency key for Kafka redelivery: one row per PlaceBidCommand.
-- Existing rows (pre-idempotency) get a synthetic id so the column can be NOT NULL.
ALTER TABLE bids ADD COLUMN command_id UUID;
UPDATE bids SET command_id = gen_random_uuid() WHERE command_id IS NULL;
ALTER TABLE bids ALTER COLUMN command_id SET NOT NULL;
ALTER TABLE bids ADD CONSTRAINT uq_bids_command_id UNIQUE (command_id);
