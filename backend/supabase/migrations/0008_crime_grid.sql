-- Migration: 0008_crime_grid.sql
-- Crime incident data and pre-computed heatmap grid for the Haulio crime overlay.
-- Heavy aggregation lives here (server-side) so mobile clients fetch lightweight tiles.

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- crime_incidents
-- Raw ingest table: one row per unique crime report from external sources.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS crime_incidents (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    type        text        NOT NULL,
    severity    int         NOT NULL DEFAULT 1,
    lat         double precision NOT NULL,
    lng         double precision NOT NULL,
    occurred_at timestamptz NOT NULL,
    source      text        NOT NULL,
    source_id   text        UNIQUE NOT NULL,   -- idempotent ingest key
    created_at  timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE crime_incidents IS
    'Raw crime incident records ingested from FBI NIBRS, NYPD, LAPD, and Chicago PD APIs. '
    'source_id is unique across sources to guarantee idempotent ingestion.';

COMMENT ON COLUMN crime_incidents.severity IS
    'Severity weight: robbery=10, assault=8, burglary=6, theft=4, vehicle=3, other=1.';

-- Spatial index on crime_incidents (lat/lng stored as a generated geography point)
ALTER TABLE crime_incidents
    ADD COLUMN IF NOT EXISTS geog geography(Point, 4326)
    GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography
    ) STORED;

CREATE INDEX IF NOT EXISTS crime_incidents_geog_idx
    ON crime_incidents USING GIST (geog);

-- ---------------------------------------------------------------------------
-- crime_grid_cells
-- Pre-computed 0.5 km heatmap grid. Rebuilt nightly by aggregate_crime_grid().
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS crime_grid_cells (
    cell_id         text        PRIMARY KEY,  -- "{lat6}_{lng6}" — rounded to 0.005 deg (~0.5 km)
    lat             double precision NOT NULL,
    lng             double precision NOT NULL,
    risk_score      numeric(5, 2) NOT NULL DEFAULT 0,
    incident_count  int          NOT NULL DEFAULT 0,
    top_crime_type  text,
    computed_at     timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE crime_grid_cells IS
    'Pre-computed 0.5 km crime heatmap grid cells. '
    'Rebuilt nightly by aggregate_crime_grid() RPC from crime_incidents (last 90 days).';

COMMENT ON COLUMN crime_grid_cells.cell_id IS
    'Composite key derived from lat and lng rounded to 3 decimal places (0.005 deg ≈ 0.5 km): '
    'format "{lat×1000_rounded}_{lng×1000_rounded}" represented as a text slug.';

-- Spatial index for bbox queries on grid cells
ALTER TABLE crime_grid_cells
    ADD COLUMN IF NOT EXISTS geog geography(Point, 4326)
    GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography
    ) STORED;

CREATE INDEX IF NOT EXISTS crime_grid_cells_geog_idx
    ON crime_grid_cells USING GIST (geog);

-- ---------------------------------------------------------------------------
-- Row-Level Security
-- ---------------------------------------------------------------------------

ALTER TABLE crime_incidents  ENABLE ROW LEVEL SECURITY;
ALTER TABLE crime_grid_cells ENABLE ROW LEVEL SECURITY;

-- Authenticated users can read all crime data (public safety information)
CREATE POLICY "auth_read_crime_incidents"
    ON crime_incidents
    FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "auth_read_crime_grid_cells"
    ON crime_grid_cells
    FOR SELECT
    TO authenticated
    USING (true);

-- Service role has unrestricted access for backend ingestion
CREATE POLICY "service_all_crime_incidents"
    ON crime_incidents
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

CREATE POLICY "service_all_crime_grid_cells"
    ON crime_grid_cells
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- ---------------------------------------------------------------------------
-- RPC: aggregate_crime_grid()
-- Rebuilds crime_grid_cells from crime_incidents using 0.5 km bins.
-- Applies severity weights and limits to last 90 days.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION aggregate_crime_grid()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Severity weight mapping
    -- robbery=10, assault=8, burglary=6, theft=4, vehicle=3, other=1

    -- Delete and re-insert all cells in a single transaction
    DELETE FROM crime_grid_cells;

    INSERT INTO crime_grid_cells (cell_id, lat, lng, risk_score, incident_count, top_crime_type, computed_at)
    SELECT
        -- Cell key: round lat/lng to nearest 0.005 degrees (~0.5 km grid)
        CONCAT(
            ROUND(lat / 0.005) * 0.005, '_',
            ROUND(lng / 0.005) * 0.005
        )::text                                 AS cell_id,

        ROUND(lat / 0.005)::double precision * 0.005   AS lat,
        ROUND(lng / 0.005)::double precision * 0.005   AS lng,

        -- Risk score: sum of severity weights, capped at 10
        LEAST(
            SUM(
                CASE
                    WHEN lower(type) IN ('robbery')              THEN 10
                    WHEN lower(type) IN ('assault', 'aggravated assault') THEN 8
                    WHEN lower(type) IN ('burglary')             THEN 6
                    WHEN lower(type) IN ('theft', 'larceny')     THEN 4
                    WHEN lower(type) IN ('vehicle', 'motor vehicle theft', 'auto theft') THEN 3
                    ELSE 1
                END
            )::numeric / NULLIF(COUNT(*), 0),
            10
        )::numeric(5,2)                         AS risk_score,

        COUNT(*)::int                           AS incident_count,

        -- Most frequent crime type in this cell
        (
            SELECT ci2.type
            FROM crime_incidents ci2
            WHERE ROUND(ci2.lat / 0.005) = ROUND(ci.lat / 0.005)
              AND ROUND(ci2.lng / 0.005) = ROUND(ci.lng / 0.005)
              AND ci2.occurred_at >= now() - INTERVAL '90 days'
            GROUP BY ci2.type
            ORDER BY COUNT(*) DESC
            LIMIT 1
        )                                       AS top_crime_type,

        now()                                   AS computed_at

    FROM crime_incidents ci
    WHERE occurred_at >= now() - INTERVAL '90 days'
    GROUP BY
        ROUND(lat / 0.005) * 0.005,
        ROUND(lng / 0.005) * 0.005;

END;
$$;

COMMENT ON FUNCTION aggregate_crime_grid() IS
    'Rebuilds crime_grid_cells from crime_incidents using 0.5 km bins. '
    'Applies severity weights (robbery=10, assault=8, burglary=6, theft=4, vehicle=3, other=1). '
    'Only considers incidents from the last 90 days. Called nightly by CrimeIngester.';
