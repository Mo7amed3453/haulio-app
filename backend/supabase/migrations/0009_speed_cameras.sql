-- Migration: 0009_speed_cameras.sql
-- Crowd-sourced speed camera reports from Haulio drivers.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- Table
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS speed_cameras_crowd (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    lat                 double precision NOT NULL,
    lng                 double precision NOT NULL,
    posted_speed_mph    int,
    reporter_driver_id  uuid            NOT NULL,
    confirmed_count     int             NOT NULL DEFAULT 1,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    last_confirmed_at   timestamptz     NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Spatial index (PostGIS GIST for ST_DWithin / proximity queries)
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS speed_cameras_crowd_geom_idx
    ON speed_cameras_crowd
    USING GIST (ST_MakePoint(lng, lat));

-- Compound index for bbox queries
CREATE INDEX IF NOT EXISTS speed_cameras_crowd_latng_idx
    ON speed_cameras_crowd (lat, lng);

-- ---------------------------------------------------------------------------
-- Row-Level Security
-- ---------------------------------------------------------------------------

ALTER TABLE speed_cameras_crowd ENABLE ROW LEVEL SECURITY;

-- Any authenticated user (driver) can read all camera reports
CREATE POLICY "auth_select_speed_cameras"
    ON speed_cameras_crowd
    FOR SELECT
    TO authenticated
    USING (true);

-- Drivers may only insert rows tied to their own account
CREATE POLICY "driver_insert_speed_cameras"
    ON speed_cameras_crowd
    FOR INSERT
    TO authenticated
    WITH CHECK (reporter_driver_id = auth.uid());

-- ---------------------------------------------------------------------------
-- RPC: confirm_camera
-- Atomically increments confirmed_count and updates last_confirmed_at.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION confirm_camera(camera_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE speed_cameras_crowd
    SET
        confirmed_count   = confirmed_count + 1,
        last_confirmed_at = now()
    WHERE id = camera_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Camera not found: %', camera_id;
    END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Auto-cleanup
-- Remove cameras older than 180 days OR with confirmed_count < 1 after
-- a 7-day re-confirm window since last_confirmed_at.
-- Invoke via Supabase pg_cron or an Edge Function cron job.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION cleanup_speed_cameras()
RETURNS void
LANGUAGE sql
AS $$
    DELETE FROM speed_cameras_crowd
    WHERE
        -- Stale: not confirmed in the last 180 days
        created_at < now() - INTERVAL '180 days'
        OR
        -- Under-confirmed: only 1 report and not re-confirmed within 7 days
        (confirmed_count < 2 AND last_confirmed_at < now() - INTERVAL '7 days');
$$;
