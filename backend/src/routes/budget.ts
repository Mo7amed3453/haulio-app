import type { FastifyPluginAsync } from 'fastify';
import { supabase } from '../lib/supabase.js';

export const budgetRoute: FastifyPluginAsync = async (fastify) => {
  // GET /v1/budget/tomtom
  fastify.get('/budget/tomtom', async (_request, reply) => {
    const today = new Date().toISOString().slice(0, 10);

    const { data, error } = await supabase
      .from('tomtom_budget')
      .select('date, mobile_calls, backend_calls')
      .eq('date', today)
      .maybeSingle();

    if (error) {
      fastify.log.error({ err: error }, 'Failed to fetch TomTom budget');
      return reply.status(500).send({ error: 'Failed to fetch budget' });
    }

    const row = data ?? { date: today, mobile_calls: 0, backend_calls: 0 };

    return {
      date: row.date,
      mobile_calls: row.mobile_calls,
      backend_calls: row.backend_calls,
      mobile_limit: 1800,
      backend_limit: 200,
      total_daily_limit: 2000,
    };
  });
};
