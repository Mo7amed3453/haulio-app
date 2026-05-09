-- Migration: 0007_fuel_eia_cache.sql
-- Cache for EIA weekly retail fuel prices per PADD district.
-- Populated hourly by the EiaCacheJob background service.

CREATE TABLE IF NOT EXISTS fuel_eia_cache (
    district    text        PRIMARY KEY,  -- EIA duoarea code e.g. "R50", "R1X"
    regular     numeric(6, 3) NOT NULL,
    week_of     date          NOT NULL,
    fetched_at  timestamptz   NOT NULL DEFAULT now()
);

COMMENT ON TABLE fuel_eia_cache IS
    'EIA weekly average retail gasoline prices per PADD district. '
    'Refreshed hourly by EiaCacheJob to reduce per-request EIA calls.';

COMMENT ON COLUMN fuel_eia_cache.district IS
    'EIA duoarea code: R1X=PADD1A, R1Y=PADD1B, R1Z=PADD1C, R20=PADD2, '
    'R30=PADD3, R40=PADD4, R50=PADD5';
