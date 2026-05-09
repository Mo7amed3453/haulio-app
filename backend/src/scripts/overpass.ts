/**
 * Shared types for OSM Overpass API responses.
 */

export interface OverpassNode {
  type: 'node';
  id: number;
  lat: number;
  lon: number;
  tags?: Record<string, string>;
}

export interface OverpassWay {
  type: 'way';
  id: number;
  nodes: number[];
  center?: { lat: number; lon: number };
  tags?: Record<string, string>;
  bounds?: { minlat: number; minlon: number; maxlat: number; maxlon: number };
}

export type OverpassElement = OverpassNode | OverpassWay;

export interface OverpassResponse {
  elements: OverpassElement[];
}

export async function queryOverpass(query: string): Promise<OverpassResponse> {
  const res = await fetch('https://overpass-api.de/api/interpreter', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `data=${encodeURIComponent(query)}`,
  });

  if (!res.ok) {
    throw new Error(`Overpass query failed: ${res.status} ${res.statusText}`);
  }

  return (await res.json()) as OverpassResponse;
}

export function bboxToOverpassStr(
  minLat: number,
  minLng: number,
  maxLat: number,
  maxLng: number,
): string {
  return `${minLat},${minLng},${maxLat},${maxLng}`;
}
