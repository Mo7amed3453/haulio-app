import { createClient } from '@supabase/supabase-js';
import { env } from '../env.js';

export const supabase = createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_KEY, {
  auth: { persistSession: false },
});

// ── Supabase type helpers ──────────────────────────────────────────────────────

export interface TrafficEvent {
  id: string;
  type: 'ACCIDENT' | 'CONSTRUCTION' | 'CLOSURE' | 'POLICE' | 'POTHOLE' | 'CONGESTION';
  severity: string | null;
  lat: number;
  lng: number;
  source: string;
  source_id: string | null;
  reporter_driver_id: string | null;
  confirmed_count: number;
  verified: boolean;
  created_at: string;
  expires_at: string;
}

export interface ExtremeZone {
  id: string;
  type: 'SCHOOL' | 'INDUSTRIAL' | 'RAIL_CROSSING' | 'HISTORICAL_CORRIDOR';
  name: string;
  geometry: unknown;
  windows: ZoneWindow[];
  priority: number;
  source: string;
  created_at: string;
}

export interface ZoneWindow {
  days: number[];
  start: string;
  end: string;
}

export interface DriverTelemetry {
  id: string;
  driver_id: string;
  lat: number;
  lng: number;
  speed_mps: number;
  heading: number;
  ts: string;
}

export interface TomTomBudget {
  date: string;
  mobile_calls: number;
  backend_calls: number;
}

export interface RerouteEvent {
  id: string;
  driver_id: string;
  trigger_type: string;
  original_route: unknown;
  new_route: unknown;
  created_at: string;
}
