import 'dotenv/config';
import { supabase } from '../lib/supabase.js';
import { queryOverpass, bboxToOverpassStr } from './overpass.js';

const SCHOOL_RADIUS_METERS = 804; // 0.5 mi
const SCHOOL_WINDOWS = [
  { days: [1, 2, 3, 4, 5], start: '07:30', end: '08:45' },
  { days: [1, 2, 3, 4, 5], start: '14:15', end: '16:00' },
];

export async function seedSchoolZones(
  minLat: number,
  minLng: number,
  maxLat: number,
  maxLng: number,
): Promise<number> {
  const bbox = bboxToOverpassStr(minLat, minLng, maxLat, maxLng);
  const query = `[out:json];node["amenity"="school"](${bbox});out body;`;

  const response = await queryOverpass(query);

  const upserts = response.elements
    .filter((el) => el.type === 'node')
    .map((el) => {
      if (el.type !== 'node') throw new Error('unreachable');
      const name = el.tags?.['name'] ?? `School ${el.id}`;
      return {
        type: 'SCHOOL',
        name,
        geometry: {
          type: 'Point',
          coordinates: [el.lon, el.lat],
        },
        radius_meters: SCHOOL_RADIUS_METERS,
        windows: SCHOOL_WINDOWS,
        priority: 2,
        source: `overpass:node/${el.id}`,
      };
    });

  if (upserts.length === 0) {
    console.log('No schools found in bbox');
    return 0;
  }

  const { error } = await supabase
    .from('extreme_zones')
    .upsert(upserts, { onConflict: 'source' });

  if (error) throw new Error(`Upsert failed: ${error.message}`);

  console.log(`Upserted ${upserts.length} school zones`);
  return upserts.length;
}
