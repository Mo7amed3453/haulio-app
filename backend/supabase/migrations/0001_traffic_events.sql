-- Migration: 0001_traffic_events
-- Traffic events reported by drivers or ingested from external sources.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS traffic_events (
  id               uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  type             text NOT NULL CHECK (type IN ('ACCIDENT','CONSTRUCTION','CLOSURE','POLICE','POTHOLE','CONGESTION')),
  severity         text,
  lat              double precision NOT NULL,
  lng              double precision NOT NULL,
  source           text NOT NULL,
  source_id        text,
  reporter_driver_id uuid,
  confirmed_count  int NOT NULL DEFAULT 1,
  verified         boolean NOT NULL DEFAULT false,
  created_at       timestamptz NOT NULL DEFAULT now(),
  expires_at       timestamptz NOT NULL
);

-- Spatial index for proximity queries
CREATE INDEX IF NOT EXISTS idx_traffic_events_location
  ON traffic_events USING GIST (ST_SetSRID(ST_MakePoint(lng, lat), 4326));

-- Index for expiry cleanup
CREATE INDEX IF NOT EXISTS idx_traffic_events_expires_at
  ON traffic_events (expires_at);

-- Index for type-based queries
CREATE INDEX IF NOT EXISTS idx_traffic_events_type
  ON traffic_events (type);

-- Enable RLS
ALTER TABLE traffic_events ENABLE ROW LEVEL SECURITY;

-- Drivers can insert their own reports
CREATE POLICY "drivers_insert_own" ON traffic_events
  FOR INSERT TO authenticated
  WITH CHECK (reporter_driver_id = auth.uid() OR reporter_driver_id IS NULL);

-- All authenticated users can SELECT non-expired events
CREATE POLICY "authenticated_select_active" ON traffic_events
  FOR SELECT TO authenticated
  USING (expires_at > now());

-- Service role bypasses RLS (used by backend)
CREATE POLICY "service_role_all" ON traffic_events
  TO service_role
  USING (true)
  WITH CHECK (true);
