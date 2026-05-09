import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { supabase } from '../lib/supabase.js';

// ---------------------------------------------------------------------------
// Query schema
// ---------------------------------------------------------------------------

const gridQuerySchema = z.object({
  bbox: z
    .string()
    .regex(/^-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*,-?\d+\.?\d*$/, {
      message: 'bbox must be "south,west,north,east" decimal numbers',
    })
    .optional(),
});

// ---------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------

export const crimeRoute: FastifyPluginAsync = async (fastify) => {
  /**
   * GET /v1/crime/grid?bbox=south,west,north,east
   *
   * Returns pre-computed crime_grid_cells within the bounding box.
   * Capped at 5 000 cells; 24 h Cache-Control header.
   */
  fastify.get('/crime/grid', async (request, reply) => {
    const parsed = gridQuerySchema.safeParse(request.query);
    if (!parsed.success) {
      return reply.status(400).send({
        error: 'Invalid query',
        details: parsed.error.flatten(),
      });
    }

    let query = supabase
      .from('crime_grid_cells')
      .select('cell_id, lat, lng, risk_score, incident_count, top_crime_type, computed_at')
      .order('risk_score', { ascending: false })
      .limit(5000);

    if (parsed.data.bbox) {
      const parts = parsed.data.bbox.split(',').map(Number);
      const [south, west, north, east] = parts as [number, number, number, number];
      query = query
        .gte('lat', south)
        .lte('lat', north)
        .gte('lng', west)
        .lte('lng', east);
    }

    const { data, error } = await query;
    if (error) {
      fastify.log.error({ err: error }, 'crimeRoute: failed to fetch grid cells');
      return reply.status(500).send({ error: 'Failed to fetch crime grid' });
    }

    // 24-hour cache — grid is only rebuilt nightly
    void reply.header('Cache-Control', 'public, max-age=86400, stale-while-revalidate=3600');

    return { cells: data ?? [] };
  });
};
