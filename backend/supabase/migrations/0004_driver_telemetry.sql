-- Migration: 0004_driver_telemetry
-- Driver GPS + speed data used for crowd-sourced speed tiles and historical corridor analysis.

CREATE TABLE IF NOT EXISTS driver_telemetry (
  id         uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  driver_id  uuid NOT NULL,
  lat        double precision NOT NULL,
  lng        double precision NOT NULL,
  speed_mps  real NOT NULL,
  heading    real,
  ts         timestamptz NOT NULL DEFAULT now()
);

-- Spatial index
CREATE INDEX IF NOT EXISTS idx_driver_telemetry_location
  ON driver_telemetry USING GIST (ST_SetSRID(ST_MakePoint(lng, lat), 4326));

-- Time index for windowed aggregation
CREATE INDEX IF NOT EXISTS idx_driver_telemetry_ts
  ON driver_telemetry (ts DESC);

-- Driver index
CREATE INDEX IF NOT EXISTS idx_driver_telemetry_driver_id
  ON driver_telemetry (driver_id);

-- Enable RLS
ALTER TABLE driver_telemetry ENABLE ROW LEVEL SECURITY;

-- Drivers can INSERT their own telemetry
CREATE POLICY "drivers_insert_own_telemetry" ON driver_telemetry
  FOR INSERT TO authenticated
  WITH CHECK (driver_id = auth.uid());

-- Service role reads all (for aggregation)
CREATE POLICY "service_role_read_all" ON driver_telemetry
  FOR SELECT TO service_role
  USING (true);

CREATE POLICY "service_role_insert_all" ON driver_telemetry
  FOR INSERT TO service_role
  WITH CHECK (true);

-- ── Historical corridor computation ─────────────────────────────────────────
-- Called by seed-historical-corridors.ts
-- Returns segments where avg speed is < threshold * free-flow for min_weeks consecutive weeks.
CREATE OR REPLACE FUNCTION compute_historical_corridors(
  speed_threshold_ratio double precision DEFAULT 0.3,
  min_weeks int DEFAULT 3
)
RETURNS TABLE (
  tile_id          text,
  centroid_lat     double precision,
  centroid_lng     double precision,
  day_of_week      int,
  hour_start       int,
  hour_end         int,
  avg_speed_ratio  double precision,
  week_count       bigint
)
LANGUAGE sql
STABLE
AS $$
  WITH bucketed AS (
    SELECT
      CONCAT(FLOOR(lat)::int, '_', FLOOR(lng)::int) AS tile_id,
      AVG(lat) AS centroid_lat,
      AVG(lng) AS centroid_lng,
      EXTRACT(DOW FROM ts)::int AS day_of_week,
      EXTRACT(HOUR FROM ts)::int AS hour_start,
      EXTRACT(HOUR FROM ts)::int + 1 AS hour_end,
      AVG(speed_mps) AS avg_speed_mps,
      DATE_TRUNC('week', ts) AS week
    FROM driver_telemetry
    WHERE ts > now() - INTERVAL '90 days'
    GROUP BY tile_id, day_of_week, hour_start, hour_end, week
  ),
  aggregated AS (
    SELECT
      tile_id,
      AVG(centroid_lat) AS centroid_lat,
      AVG(centroid_lng) AS centroid_lng,
      day_of_week,
      hour_start,
      hour_end,
      AVG(avg_speed_mps) / NULLIF(MAX(avg_speed_mps) OVER (PARTITION BY tile_id), 0) AS avg_speed_ratio,
      COUNT(DISTINCT week) AS week_count
    FROM bucketed
    GROUP BY tile_id, day_of_week, hour_start, hour_end
  )
  SELECT
    tile_id, centroid_lat, centroid_lng,
    day_of_week, hour_start, hour_end,
    avg_speed_ratio, week_count
  FROM aggregated
  WHERE avg_speed_ratio < speed_threshold_ratio
    AND week_count >= min_weeks
  ORDER BY avg_speed_ratio ASC;
$$;

-- ── Expire incidents by type ─────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION expire_incidents_by_type(
  event_type text,
  ttl_interval text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE traffic_events
  SET expires_at = now()
  WHERE type = event_type
    AND expires_at > now()
    AND created_at < (now() - ttl_interval::interval);
END;
$$;

-- ── Confirmation aggregator ──────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION aggregate_incident_confirmations(
  radius_meters int DEFAULT 200,
  window_minutes int DEFAULT 30,
  threshold int DEFAULT 3
)
RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  updated_count int;
BEGIN
  WITH candidates AS (
    SELECT a.id,
           COUNT(DISTINCT b.id) AS nearby_count
    FROM traffic_events a
    JOIN traffic_events b ON
      a.type = b.type
      AND a.id <> b.id
      AND ST_DWithin(
        ST_SetSRID(ST_MakePoint(a.lng, a.lat), 4326)::geography,
        ST_SetSRID(ST_MakePoint(b.lng, b.lat), 4326)::geography,
        radius_meters
      )
      AND ABS(EXTRACT(EPOCH FROM (a.created_at - b.created_at))) < window_minutes * 60
    WHERE a.expires_at > now()
      AND NOT a.verified
    GROUP BY a.id
    HAVING COUNT(DISTINCT b.id) + 1 >= threshold
  )
  UPDATE traffic_events
  SET verified = true, confirmed_count = (SELECT nearby_count + 1 FROM candidates c WHERE c.id = traffic_events.id)
  WHERE id IN (SELECT id FROM candidates);

  GET DIAGNOSTICS updated_count = ROW_COUNT;
  RETURN updated_count;
END;
$$;
