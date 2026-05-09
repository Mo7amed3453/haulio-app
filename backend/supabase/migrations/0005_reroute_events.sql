-- Migration: 0005_reroute_events
-- Analytics table for AutoRerouteEngine activations.

CREATE TABLE IF NOT EXISTS reroute_events (
  id               uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  driver_id        uuid NOT NULL,
  trigger_type     text NOT NULL,
  original_route   jsonb,
  new_route        jsonb,
  created_at       timestamptz NOT NULL DEFAULT now()
);

-- Index for per-driver analytics
CREATE INDEX IF NOT EXISTS idx_reroute_events_driver_id
  ON reroute_events (driver_id);

-- Index for time-series analytics
CREATE INDEX IF NOT EXISTS idx_reroute_events_created_at
  ON reroute_events (created_at DESC);

-- Enable RLS
ALTER TABLE reroute_events ENABLE ROW LEVEL SECURITY;

-- Service role can insert and read
CREATE POLICY "service_role_all_reroute" ON reroute_events
  TO service_role
  USING (true)
  WITH CHECK (true);

-- Drivers can read their own reroute events
CREATE POLICY "drivers_read_own_reroute" ON reroute_events
  FOR SELECT TO authenticated
  USING (driver_id = auth.uid());
