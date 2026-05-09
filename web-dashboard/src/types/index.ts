// ─── Traffic Events ──────────────────────────────────────────────────────────

export type IncidentType =
  | 'accident'
  | 'congestion'
  | 'road_closure'
  | 'construction'
  | 'weather'
  | 'other';

export interface TrafficEvent {
  id: string;
  created_at: string;
  type: IncidentType;
  lat: number;
  lng: number;
  severity: number; // 1–5
  description: string;
  active: boolean;
}

// ─── Extreme Zones ───────────────────────────────────────────────────────────

export type ZoneType =
  | 'school'
  | 'industrial'
  | 'rail_crossing'
  | 'historical_corridor';

export interface TimeWindow {
  days: number[]; // 0 = Sunday … 6 = Saturday
  start: string;  // "HH:MM" local
  end: string;    // "HH:MM" local
  label?: string;
}

export type GeoJsonPoint = {
  type: 'Point';
  coordinates: [number, number]; // [lng, lat]
};

export type GeoJsonPolygon = {
  type: 'Polygon';
  coordinates: [number, number][][];
};

export type GeoJsonLineString = {
  type: 'LineString';
  coordinates: [number, number][];
};

export type GeoJsonMultiLineString = {
  type: 'MultiLineString';
  coordinates: [number, number][][];
};

export type GeoJsonGeometry =
  | GeoJsonPoint
  | GeoJsonPolygon
  | GeoJsonLineString
  | GeoJsonMultiLineString;

export interface ExtremeZone {
  id: string;
  name: string;
  type: ZoneType;
  geometry: GeoJsonGeometry;
  windows: TimeWindow[];
  priority: number; // 1–5
  created_at: string;
}

// ─── Driver Telemetry ─────────────────────────────────────────────────────────

export interface DriverTelemetry {
  id: string;
  driver_id: string;
  lat: number;
  lng: number;
  speed: number; // km/h
  timestamp: string;
}

// ─── Zone Editor form ─────────────────────────────────────────────────────────

export interface ZoneFormValues {
  name: string;
  type: ZoneType;
  priority: number;
  windows: TimeWindow[];
  geometry: GeoJsonGeometry | null;
}
