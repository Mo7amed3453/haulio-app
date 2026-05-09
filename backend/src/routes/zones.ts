import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { supabase } from '../lib/supabase.js';

const listZonesQuery = z.object({
  bbox: z
    .string()
    .regex(/^-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*$/)
    .optional(),
});

export const zonesRoute: FastifyPluginAsync = async (fastify) => {
  // GET /v1/zones?bbox=minLat,minLng,maxLat,maxLng
  fastify.get('/zones', async (request, reply) => {
    const parsed = listZonesQuery.safeParse(request.query);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid query', details: parsed.error.flatten() });
    }

    let query = supabase
      .from('extreme_zones')
      .select('id, type, name, geometry, windows, priority')
      .order('priority', { ascending: true })
      .limit(1000);

    if (parsed.data.bbox) {
      // PostGIS: ST_Intersects with bbox
      // For supabase-js we pass a raw RPC call or filter; here we approximate with centroid bounds
      const parts = parsed.data.bbox.split(',').map(Number) as [number, number, number, number];
      const [minLat, minLng, maxLat, maxLng] = parts;
      // Use PostGIS via rpc for proper spatial filtering
      const { data, error } = await supabase.rpc('zones_in_bbox', {
        min_lat: minLat,
        min_lng: minLng,
        max_lat: maxLat,
        max_lng: maxLng,
      });
      if (error) {
        fastify.log.error({ err: error }, 'Failed to fetch zones with bbox');
        return reply.status(500).send({ error: 'Failed to fetch zones' });
      }
      return { zones: data ?? [] };
    }

    const { data, error } = await query;
    if (error) {
      fastify.log.error({ err: error }, 'Failed to fetch zones');
      return reply.status(500).send({ error: 'Failed to fetch zones' });
    }

    return { zones: data ?? [] };
  });
};
