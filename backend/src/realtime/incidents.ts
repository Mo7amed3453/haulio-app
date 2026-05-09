/**
 * Supabase Realtime subscription helper.
 *
 * Mobile clients use this channel pattern to subscribe to traffic_events
 * filtered by proximity (ST_DWithin server-side via PostGIS RLS/function).
 *
 * Usage example (client-side):
 *
 *   import { createClient } from '@supabase/supabase-js';
 *
 *   const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
 *   const channel = subscribeToNearbyIncidents(supabase, 37.7749, -122.4194, (event) => {
 *     console.log('New incident:', event);
 *   });
 *   // To unsubscribe:
 *   await channel.unsubscribe();
 */

import type { SupabaseClient, RealtimePostgresInsertPayload } from '@supabase/supabase-js';
import type { TrafficEvent } from '../lib/supabase.js';

const NEARBY_RADIUS_METERS = 5000;

export interface NearbyIncidentPayload {
  event: TrafficEvent;
  distanceMeters?: number;
}

export type IncidentCallback = (payload: NearbyIncidentPayload) => void;

/**
 * Subscribe to new traffic_events within `radiusMeters` of (lat, lng).
 * The server-side PostGIS filter `ST_DWithin` enforces the radius.
 */
export function subscribeToNearbyIncidents(
  client: SupabaseClient,
  lat: number,
  lng: number,
  callback: IncidentCallback,
  radiusMeters: number = NEARBY_RADIUS_METERS,
) {
  const channel = client
    .channel(`nearby-incidents:${lat.toFixed(4)},${lng.toFixed(4)}`)
    .on<TrafficEvent>(
      'postgres_changes',
      {
        event: 'INSERT',
        schema: 'public',
        table: 'traffic_events',
        // Server-side filter (requires matching RLS + Realtime filter setup)
        filter: `expires_at=gt.${new Date().toISOString()}`,
      },
      (payload: RealtimePostgresInsertPayload<TrafficEvent>) => {
        const event = payload.new;
        // Client-side distance check (lat/lng Euclidean approximation for quick pre-filter)
        const approxDist = haversineMeters(lat, lng, event.lat, event.lng);
        if (approxDist <= radiusMeters) {
          callback({ event, distanceMeters: approxDist });
        }
      },
    )
    .subscribe();

  return channel;
}

// ── Haversine ─────────────────────────────────────────────────────────────────

function haversineMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6_371_000;
  const toRad = (d: number): number => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}
