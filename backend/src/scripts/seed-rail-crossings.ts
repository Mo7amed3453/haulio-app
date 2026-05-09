import 'dotenv/config';
import { supabase } from '../lib/supabase.js';
import { queryOverpass, bboxToOverpassStr } from './overpass.js';
import { z } from 'zod';

/** transit.land free GTFS stop_times via their v2 API */
const TRANSITLAND_API = 'https://transit.land/api/v2/rest';
const BEFORE_TRAIN_MIN = 3;
const AFTER_TRAIN_MIN = 5;

const StopTimeSchema = z.object({
  departure_time: z.string(), // "HH:MM:SS"
  trip: z.object({
    service_days_of_week: z.array(z.boolean()).length(7),
  }),
});

const StopTimesResponse = z.object({
  stop_times: z.array(StopTimeSchema),
});

function hhmmsToMinutes(hhmmss: string): number {
  const parts = hhmmss.split(':').map(Number) as [number, number, number];
  return parts[0] * 60 + parts[1] + Math.round(parts[2] / 60);
}

function minutesToHhmm(minutes: number): string {
  const h = Math.floor(minutes / 60) % 24;
  const m = minutes % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/** Fetch scheduled train departures near a lat/lng via transit.land */
async function fetchNearbyStopTimes(
  lat: number,
  lon: number,
): Promise<Array<{ start: string; end: string; days: number[] }>> {
  const apiKey = process.env['TRANSITLAND_API_KEY'] ?? '';
  const url =
    `${TRANSITLAND_API}/stop_times?` +
    `lat=${lat}&lon=${lon}&radius=500&per_page=50` +
    (apiKey ? `&apikey=${apiKey}` : '');

  const res = await fetch(url);
  if (!res.ok) return []; // graceful degradation

  const raw: unknown = await res.json();
  const parsed = StopTimesResponse.safeParse(raw);
  if (!parsed.success) return [];

  const windows: Array<{ start: string; end: string; days: number[] }> = [];

  for (const st of parsed.data.stop_times) {
    const depMin = hhmmsToMinutes(st.departure_time);
    const startMin = depMin - BEFORE_TRAIN_MIN;
    const endMin = depMin + AFTER_TRAIN_MIN;

    // Convert transit.land boolean array [Mon,Tue,Wed,Thu,Fri,Sat,Sun] to day ints
    const days: number[] = [];
    const dayMap = [1, 2, 3, 4, 5, 6, 0]; // Mon=1...Sun=0 (JS Date.getDay convention)
    st.trip.service_days_of_week.forEach((active, idx) => {
      const mappedIdx = dayMap[idx];
      if (active && mappedIdx !== undefined) days.push(mappedIdx);
    });

    if (days.length > 0) {
      windows.push({ start: minutesToHhmm(startMin), end: minutesToHhmm(endMin), days });
    }
  }

  return windows;
}

export async function seedRailCrossings(
  minLat: number,
  minLng: number,
  maxLat: number,
  maxLng: number,
): Promise<number> {
  const bbox = bboxToOverpassStr(minLat, minLng, maxLat, maxLng);
  const query = `[out:json];node["railway"="level_crossing"](${bbox});out body;`;

  const response = await queryOverpass(query);

  const nodes = response.elements.filter((el) => el.type === 'node');
  const upserts: Array<Record<string, unknown>> = [];

  for (const el of nodes) {
    if (el.type !== 'node') continue;

    const windows = await fetchNearbyStopTimes(el.lat, el.lon);
    const finalWindows = windows.length > 0 ? windows : []; // no windows = passthrough only

    upserts.push({
      type: 'RAIL_CROSSING',
      name: el.tags?.['name'] ?? `Rail Crossing ${el.id}`,
      geometry: {
        type: 'Point',
        coordinates: [el.lon, el.lat],
      },
      radius_meters: 200,
      windows: finalWindows,
      priority: 1,
      source: `overpass:node/${el.id}`,
    });
  }

  if (upserts.length === 0) {
    console.log('No rail crossings found in bbox');
    return 0;
  }

  const { error } = await supabase
    .from('extreme_zones')
    .upsert(upserts, { onConflict: 'source' });

  if (error) throw new Error(`Upsert failed: ${error.message}`);

  console.log(`Upserted ${upserts.length} rail crossings`);
  return upserts.length;
}
