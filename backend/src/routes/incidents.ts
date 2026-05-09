import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { supabase } from '../lib/supabase.js';

const createIncidentBody = z.object({
  type: z.enum(['ACCIDENT', 'CONSTRUCTION', 'CLOSURE', 'POLICE', 'POTHOLE', 'CONGESTION']),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  severity: z.string().optional(),
});

const listIncidentsQuery = z.object({
  bbox: z
    .string()
    .regex(/^-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*$/)
    .optional(),
});

export const incidentsRoute: FastifyPluginAsync = async (fastify) => {
  // POST /v1/incidents
  fastify.post('/incidents', async (request, reply) => {
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'Authorization required' });
    }

    const parsed = createIncidentBody.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid body', details: parsed.error.flatten() });
    }

    const { type, lat, lng, severity } = parsed.data;

    // Derive driver id from JWT sub (simplistic – real impl uses supabase.auth.getUser)
    const token = authHeader.slice(7);
    const payload = parseJwtPayload(token);
    const reporterDriverId = (payload?.sub as string | undefined) ?? null;

    // Compute expiry by type
    const expiresAt = getExpiresAt(type);

    const { data, error } = await supabase
      .from('traffic_events')
      .insert({
        type,
        lat,
        lng,
        severity: severity ?? null,
        source: 'driver_report',
        source_id: null,
        reporter_driver_id: reporterDriverId,
        expires_at: expiresAt,
      })
      .select()
      .single();

    if (error) {
      fastify.log.error({ err: error }, 'Failed to insert incident');
      return reply.status(500).send({ error: 'Failed to create incident' });
    }

    return reply.status(201).send(data);
  });

  // GET /v1/incidents?bbox=minLat,minLng,maxLat,maxLng
  fastify.get('/incidents', async (request, reply) => {
    const parsed = listIncidentsQuery.safeParse(request.query);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid query', details: parsed.error.flatten() });
    }

    const now = new Date().toISOString();
    let query = supabase
      .from('traffic_events')
      .select('*')
      .gt('expires_at', now)
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
      fastify.log.error({ err: error }, 'Failed to fetch incidents');
      return reply.status(500).send({ error: 'Failed to fetch incidents' });
    }

    return { incidents: data ?? [] };
  });
};

// ── Helpers ────────────────────────────────────────────────────────────────────

function getExpiresAt(type: string): string {
  const now = Date.now();
  const intervals: Record<string, number> = {
    ACCIDENT: 2 * 60 * 60 * 1000,
    CONSTRUCTION: 8 * 60 * 60 * 1000,
    CLOSURE: 24 * 60 * 60 * 1000,
    POLICE: 1 * 60 * 60 * 1000,
    POTHOLE: 14 * 24 * 60 * 60 * 1000,
    CONGESTION: 1 * 60 * 60 * 1000,
  };
  const ms = intervals[type] ?? 2 * 60 * 60 * 1000;
  return new Date(now + ms).toISOString();
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
