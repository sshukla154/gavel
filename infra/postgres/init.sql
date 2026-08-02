-- Create database for bid-service.
-- Runs once on first container start (postgres-data volume must be empty).
-- To re-run: docker compose down -v && docker compose up postgres
CREATE DATABASE bids_db;
