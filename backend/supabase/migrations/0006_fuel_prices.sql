-- Migration: 0006_fuel_prices.sql
-- Crowd-sourced fuel price reports from Haulio drivers.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS fuel_prices_crowd (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    station_id          text        NOT NULL,
    lat                 double precision NOT NULL,
    lng                 double precision NOT NULL,
    regular             numeric(6, 3),
    mid                 numeric(6, 3),
    premium             numeric(6, 3),
    diesel              numeric(6, 3),
    reporter_driver_id  uuid,
    created_at          timestamptz NOT NULL DEFAULT now()
);

-- Spatial index for bbox queries
CREATE INDEX IF NOT EXISTS fuel_prices_crowd_latng_idx
    ON fuel_prices_crowd (lat, lng);

-- Index for per-station lookups
CREATE INDEX IF NOT EXISTS fuel_prices_crowd_station_idx
    ON fuel_prices_crowd (station_id, created_at DESC);

-- Auto-cleanup: remove rows older than 30 days.
-- Runs via Supabase Edge Function cron or a pg_cron job.
-- The application-level EiaCacheJob also respects this TTL.
CREATE OR REPLACE FUNCTION delete_old_fuel_reports()
RETURNS void
LANGUAGE sql
AS $$
    DELETE FROM fuel_prices_crowd
    WHERE created_at < now() - INTERVAL '30 days';
$$;

-- Row-Level Security
ALTER TABLE fuel_prices_crowd ENABLE ROW LEVEL SECURITY;

-- Any authenticated user can read crowd reports
CREATE POLICY "auth_select_fuel_crowd"
    ON fuel_prices_crowd
    FOR SELECT
    TO authenticated
    USING (true);

-- Drivers may only insert rows linked to their own account
CREATE POLICY "driver_insert_fuel_crowd"
    ON fuel_prices_crowd
    FOR INSERT
    TO authenticated
    WITH CHECK (reporter_driver_id = auth.uid());
