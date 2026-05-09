import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { supabase } from '../lib/supabase.js';

const rerouteEventBody = z.object({
  driver_id: z.string().uuid(),
  trigger_type: z.string().min(1),
  original_route: z.unknown(),
  new_route: z.unknown(),
});

export const rerouteRoute: FastifyPluginAsync = async (fastify) => {
  // POST /v1/reroute-event
  fastify.post('/reroute-event', async (request, reply) => {
    const parsed = rerouteEventBody.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid body', details: parsed.error.flatten() });
    }

    const { driver_id, trigger_type, original_route, new_route } = parsed.data;

    const { data, error } = await supabase
      .from('reroute_events')
      .insert({ driver_id, trigger_type, original_route, new_route })
      .select()
      .single();

    if (error) {
      fastify.log.error({ err: error }, 'Failed to insert reroute event');
      return reply.status(500).send({ error: 'Failed to record reroute event' });
    }

    return reply.status(201).send(data);
  });
};
