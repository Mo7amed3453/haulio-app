import 'dotenv/config';
import { supabase } from '../lib/supabase.js';
import { queryOverpass, bboxToOverpassStr, type OverpassWay } from './overpass.js';

const INDUSTRIAL_RADIUS_METERS = 1609; // 1 mi
const INDUSTRIAL_WINDOWS = [
  { days: [1, 2, 3, 4, 5], start: '06:00', end: '08:00' },
  { days: [1, 2, 3, 4, 5], start: '14:30', end: '16:30' },
  { days: [1, 2, 3, 4, 5], start: '22:00', end: '23:59' },
];

function centroidOfWay(way: OverpassWay): { lat: number; lon: number } {
  if (way.center) return way.center;
  if (way.bounds) {
    return {
      lat: (way.bounds.minlat + way.bounds.maxlat) / 2,
      lon: (way.bounds.minlon + way.bounds.maxlon) / 2,
    };
  }
  throw new Error(`Way ${way.id} has no center or bounds`);
}

export async function seedIndustrialZones(
  minLat: number,
  minLng: number,
  maxLat: number,
  maxLng: number,
): Promise<number> {
  const bbox = bboxToOverpassStr(minLat, minLng, maxLat, maxLng);
  const query = `[out:json];way["landuse"="industrial"](${bbox});out center;`;

  const response = await queryOverpass(query);

  const upserts: Array<Record<string, unknown>> = [];

  for (const el of response.elements) {
    if (el.type !== 'way') continue;

    let centroid: { lat: number; lon: number };
    try {
      centroid = centroidOfWay(el);
    } catch {
      continue;
    }

    const name = el.tags?.['name'] ?? `Industrial zone ${el.id}`;
    upserts.push({
      type: 'INDUSTRIAL',
      name,
      geometry: {
        type: 'Point',
        coordinates: [centroid.lon, centroid.lat],
      },
      radius_meters: INDUSTRIAL_RADIUS_METERS,
      windows: INDUSTRIAL_WINDOWS,
      priority: 3,
      source: `overpass:way/${el.id}`,
    });
  }

  if (upserts.length === 0) {
    console.log('No industrial zones found in bbox');
    return 0;
  }

  const { error } = await supabase
    .from('extreme_zones')
    .upsert(upserts, { onConflict: 'source' });

  if (error) throw new Error(`Upsert failed: ${error.message}`);

  console.log(`Upserted ${upserts.length} industrial zones`);
  return upserts.length;
}
