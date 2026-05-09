-- Migration: 0002_extreme_zones
-- Static/semi-static zones: schools, industrial areas, rail crossings, historical corridors.

CREATE TABLE IF NOT EXISTS extreme_zones (
  id           uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  type         text NOT NULL CHECK (type IN ('SCHOOL','INDUSTRIAL','RAIL_CROSSING','HISTORICAL_CORRIDOR')),
  name         text NOT NULL,
  geometry     geography(Point, 4326) NOT NULL,
  radius_meters int NOT NULL DEFAULT 500,
  windows      jsonb NOT NULL DEFAULT '[]',
  priority     int NOT NULL DEFAULT 5,
  source       text NOT NULL UNIQUE,
  created_at   timestamptz NOT NULL DEFAULT now()
);

-- Spatial index for bounding-box queries
CREATE INDEX IF NOT EXISTS idx_extreme_zones_geometry
  ON extreme_zones USING GIST (geometry);

-- Priority index for ordered retrieval
CREATE INDEX IF NOT EXISTS idx_extreme_zones_priority
  ON extreme_zones (priority ASC);

-- Type index
CREATE INDEX IF NOT EXISTS idx_extreme_zones_type
  ON extreme_zones (type);

-- RPC: zones_in_bbox (used by GET /v1/zones?bbox=...)
CREATE OR REPLACE FUNCTION zones_in_bbox(
  min_lat double precision,
  min_lng double precision,
  max_lat double precision,
  max_lng double precision
)
RETURNS SETOF extreme_zones
LANGUAGE sql
STABLE
AS $$
  SELECT * FROM extreme_zones
  WHERE ST_Intersects(
    geometry,
    ST_MakeEnvelope(min_lng, min_lat, max_lng, max_lat, 4326)::geography
  )
  ORDER BY priority ASC
  LIMIT 1000;
$$;
