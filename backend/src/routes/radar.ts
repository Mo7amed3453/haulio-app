import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { supabase } from '../lib/supabase.js';

// ---------------------------------------------------------------------------
// Validation schemas
// ---------------------------------------------------------------------------

const submitCameraBody = z.object({
  lat:           z.number().min(-90).max(90),
  lng:           z.number().min(-180).max(180),
  postedSpeedMph: z.number().int().min(5).max(100).optional(),
});

const confirmCameraParams = z.object({
  id: z.string().uuid(),
});

const bboxQuery = z.object({
  bbox: z
    .string()
    .regex(/^-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*$/, {
      message: 'bbox must be minLat,minLng,maxLat,maxLng',
    })
    .optional(),
});

// ---------------------------------------------------------------------------
// Rate-limit store (in-memory; swap for Redis in production)
// ---------------------------------------------------------------------------

const SUBMIT_WINDOW_MS   = 60 * 60 * 1000; // 1 hour
const MAX_SUBMITS_PER_HOUR = 10;

const submitTracker = new Map<string, number[]>();

function isSubmitRateLimited(driverId: string): boolean {
  const now    = Date.now();
  const cutoff = now - SUBMIT_WINDOW_MS;
  const times  = (submitTracker.get(driverId) ?? []).filter((t) => t > cutoff);
  if (times.length >= MAX_SUBMITS_PER_HOUR) return true;
  times.push(now);
  submitTracker.set(driverId, times);
  return false;
}

// ---------------------------------------------------------------------------
// Route plugin
// ---------------------------------------------------------------------------

export const radarRoute: FastifyPluginAsync = async (fastify) => {

  /**
   * POST /v1/radar/cameras
   * Submit a crowd-reported speed camera.
   * Requires: Authorization: Bearer <jwt>
   * Rate-limited to 10 submissions per hour per driver.
   */
  fastify.post('/radar/cameras', async (request, reply) => {
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'Authorization required' });
    }

    const parsed = submitCameraBody.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid body', details: parsed.error.flatten() });
    }

    const { lat, lng, postedSpeedMph } = parsed.data;

    const token          = authHeader.slice(7);
    const payload        = parseJwtPayload(token);
    const reporterDriverId = (payload?.['sub'] as string | undefined) ?? null;

    if (!reporterDriverId) {
      return reply.status(401).send({ error: 'Invalid or expired token' });
    }

    if (isSubmitRateLimited(reporterDriverId)) {
      return reply.status(429).send({ error: 'Rate limit exceeded — max 10 submissions per hour' });
    }

    const { data, error } = await supabase
      .from('speed_cameras_crowd')
      .insert({
        lat,
        lng,
        posted_speed_mph:   postedSpeedMph ?? null,
        reporter_driver_id: reporterDriverId,
      })
      .select()
      .single();

    if (error) {
      fastify.log.error({ err: error }, 'Failed to insert speed camera');
      return reply.status(500).send({ error: 'Failed to submit speed camera' });
    }

    return reply.status(201).send(data);
  });

  /**
   * POST /v1/radar/cameras/:id/confirm
   * Confirms an existing crowd-reported speed camera.
   * Requires: Authorization: Bearer <jwt>
   */
  fastify.post('/radar/cameras/:id/confirm', async (request, reply) => {
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'Authorization required' });
    }

    const paramsParsed = confirmCameraParams.safeParse(request.params);
    if (!paramsParsed.success) {
      return reply.status(400).send({ error: 'Invalid camera ID — must be a UUID' });
    }

    const { id: cameraId } = paramsParsed.data;

    const { error } = await supabase.rpc('confirm_camera', { camera_id: cameraId });

    if (error) {
      fastify.log.error({ err: error }, 'Failed to confirm camera');
      if (error.message?.includes('not found')) {
        return reply.status(404).send({ error: 'Camera not found' });
      }
      return reply.status(500).send({ error: 'Failed to confirm camera' });
    }

    return reply.status(200).send({ ok: true });
  });

  /**
   * GET /v1/radar/cameras?bbox=minLat,minLng,maxLat,maxLng
   * Returns crowd-sourced cameras within the given bounding box.
   * OSM cameras are fetched directly by the client via the Overpass API.
   * Requires: Authorization: Bearer <jwt>
   */
  fastify.get('/radar/cameras', async (request, reply) => {
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'Authorization required' });
    }

    const parsed = bboxQuery.safeParse(request.query);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid query', details: parsed.error.flatten() });
    }

    let query = supabase
      .from('speed_cameras_crowd')
      .select('id, lat, lng, posted_speed_mph, confirmed_count, created_at, last_confirmed_at')
      .order('created_at', { ascending: false })
      .limit(500);

    if (parsed.data.bbox) {
      const parts = parsed.data.bbox.split(',').map(Number) as [number, number, number, number];
      const [minLat, minLng, maxLat, maxLng] = parts;
      query = query
        .gte('lat', minLat)
        .lte('lat', maxLat)
        .gte('lng', minLng)
        .lte('lng', maxLng);
    }

    const { data, error } = await query;
    if (error) {
      fastify.log.error({ err: error }, 'Failed to fetch speed cameras');
      return reply.status(500).send({ error: 'Failed to fetch speed cameras' });
    }

    return { cameras: data ?? [] };
  });
};

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

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
