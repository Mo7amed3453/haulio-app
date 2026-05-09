import 'dotenv/config';
import { supabase } from '../lib/supabase.js';

const FREE_FLOW_SPEED_THRESHOLD = 0.3; // avg speed < 30% of free-flow
const MIN_WEEKS = 3;

interface CongestionSegment {
  tile_id: string;
  centroid_lat: number;
  centroid_lng: number;
  day_of_week: number;
  hour_start: number;
  hour_end: number;
  avg_speed_ratio: number;
  week_count: number;
}

/**
 * Identify segments where avg speed is < 30% of free-flow consistently for
 * 3+ weeks on the same day/hour. Uses driver_telemetry aggregations.
 */
export async function seedHistoricalCorridors(): Promise<number> {
  // Call a Postgres stored function that does the heavy spatial aggregation
  const { data, error } = await supabase.rpc('compute_historical_corridors', {
    speed_threshold_ratio: FREE_FLOW_SPEED_THRESHOLD,
    min_weeks: MIN_WEEKS,
  });

  if (error) throw new Error(`Historical corridor RPC failed: ${error.message}`);

  const segments = (data as CongestionSegment[] | null) ?? [];

  if (segments.length === 0) {
    console.log('No historical corridors found');
    return 0;
  }

  const upserts = segments.map((seg) => ({
    type: 'HISTORICAL_CORRIDOR',
    name: `Historical Corridor (tile ${seg.tile_id})`,
    geometry: {
      type: 'Point',
      coordinates: [seg.centroid_lng, seg.centroid_lat],
    },
    radius_meters: 500,
    windows: [
      {
        days: [seg.day_of_week],
        start: `${String(seg.hour_start).padStart(2, '0')}:00`,
        end: `${String(seg.hour_end).padStart(2, '0')}:00`,
      },
    ],
    priority: 4,
    source: `telemetry:tile/${seg.tile_id}/dow/${seg.day_of_week}/h/${seg.hour_start}`,
  }));

  const { error: upsertError } = await supabase
    .from('extreme_zones')
    .upsert(upserts, { onConflict: 'source' });

  if (upsertError) throw new Error(`Upsert failed: ${upsertError.message}`);

  console.log(`Upserted ${upserts.length} historical corridors`);
  return upserts.length;
}
