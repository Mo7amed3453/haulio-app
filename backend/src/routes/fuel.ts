import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { supabase } from '../lib/supabase.js';

// ---------------------------------------------------------------------------
// Validation schemas
// ---------------------------------------------------------------------------

const reportFuelBody = z.object({
  stationId: z.string().min(1),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  grades: z.object({
    regular:  z.number().positive().optional(),
    mid:      z.number().positive().optional(),
    premium:  z.number().positive().optional(),
    diesel:   z.number().positive().optional(),
  }),
});

const nearbyFuelQuery = z.object({
  bbox: z
    .string()
    .regex(/^-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*$/, {
      message: 'bbox must be minLat,minLng,maxLat,maxLng',
    })
    .optional(),
});

// ---------------------------------------------------------------------------
// Rate-limit store (in-memory; use Redis in production)
// ---------------------------------------------------------------------------

const submissionWindow = 60 * 60 * 1000; // 1 hour in ms
const maxSubmissionsPerHour = 5;

const submissionTracker = new Map<string, number[]>();

function isRateLimited(driverId: string): boolean {
  const now = Date.now();
  const cutoff = now - submissionWindow;
  const times = (submissionTracker.get(driverId) ?? []).filter((t) => t > cutoff);
  if (times.length >= maxSubmissionsPerHour) return true;
  times.push(now);
  submissionTracker.set(driverId, times);
  return false;
}

// ---------------------------------------------------------------------------
// Route plugin
// ---------------------------------------------------------------------------

export const fuelRoute: FastifyPluginAsync = async (fastify) => {

  /**
   * POST /v1/fuel/report
   * Submit a crowd-sourced fuel price for a station.
   * Requires: Authorization: Bearer <jwt>
   * Rate-limited to 5 submissions per hour per driver.
   */
  fastify.post('/fuel/report', async (request, reply) => {
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'Authorization required' });
    }

    const parsed = reportFuelBody.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid body', details: parsed.error.flatten() });
    }

    const { stationId, lat, lng, grades } = parsed.data;

    // Validate that at least one grade price is provided
    if (!grades.regular && !grades.mid && !grades.premium && !grades.diesel) {
      return reply.status(400).send({ error: 'At least one grade price is required' });
    }

    const token = authHeader.slice(7);
    const payload = parseJwtPayload(token);
    const reporterDriverId = (payload?.sub as string | undefined) ?? null;

    if (reporterDriverId && isRateLimited(reporterDriverId)) {
      return reply.status(429).send({ error: 'Rate limit exceeded — max 5 submissions per hour' });
    }

    const { data, error } = await supabase
      .from('fuel_prices_crowd')
      .insert({
        station_id:          stationId,
        lat,
        lng,
        regular:             grades.regular ?? null,
        mid:                 grades.mid ?? null,
        premium:             grades.premium ?? null,
        diesel:              grades.diesel ?? null,
        reporter_driver_id:  reporterDriverId,
      })
      .select()
      .single();

    if (error) {
      fastify.log.error({ err: error }, 'Failed to insert fuel report');
      return reply.status(500).send({ error: 'Failed to submit fuel report' });
    }

    return reply.status(201).send(data);
  });

  /**
   * GET /v1/fuel/nearby?bbox=minLat,minLng,maxLat,maxLng
   * Returns the latest crowd-sourced fuel price per station within the bbox.
   * Open to authenticated users.
   */
  fastify.get('/fuel/nearby', async (request, reply) => {
    const parsed = nearbyFuelQuery.safeParse(request.query);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid query', details: parsed.error.flatten() });
    }

    let query = supabase
      .from('fuel_prices_crowd')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(500);

    if (parsed.data.bbox) {
      const parts = parsed.data.bbox.split(',').map(Number);
      const [minLat, minLng, maxLat, maxLng] = parts as [number, number, number, number];
      query = query
        .gte('lat', minLat)
        .lte('lat', maxLat)
        .gte('lng', minLng)
        .lte('lng', maxLng);
    }

    const { data, error } = await query;
    if (error) {
      fastify.log.error({ err: error }, 'Failed to fetch fuel reports');
      return reply.status(500).send({ error: 'Failed to fetch fuel reports' });
    }

    // Merge: keep only the latest report per station_id
    const latestByStation = mergeLatestPerStation(data ?? []);

    return { stations: latestByStation };
  });
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface FuelRow {
  id: string;
  station_id: string;
  lat: number;
  lng: number;
  regular: number | null;
  mid: number | null;
  premium: number | null;
  diesel: number | null;
  reporter_driver_id: string | null;
  created_at: string;
}

function mergeLatestPerStation(rows: FuelRow[]): FuelRow[] {
  const seen = new Set<string>();
  const result: FuelRow[] = [];
  for (const row of rows) {
    if (!seen.has(row.station_id)) {
      seen.add(row.station_id);
      result.push(row);
    }
  }
  return result;
}

function parseJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3 || !parts[1]) return null;
    const raw = Buffer.from(parts[1], 'base64url').toString('utf-8');
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }
}
