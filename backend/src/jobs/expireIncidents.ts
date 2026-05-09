import cron from 'node-cron';
import type { Logger } from 'pino';
import { supabase } from '../lib/supabase.js';

/**
 * Hourly cron: expire traffic_events based on type-specific TTLs.
 *
 * TTLs:
 *   ACCIDENT     – 2 h
 *   CONSTRUCTION – 8 h
 *   CLOSURE      – 24 h
 *   POLICE       – 1 h
 *   POTHOLE      – 14 d
 *   CONGESTION   – 1 h (default)
 */
export function startExpireJob(log: Logger): void {
  cron.schedule('0 * * * *', () => {
    expireIncidents(log).catch((err: unknown) => {
      log.error({ err }, 'expireIncidents cron unhandled error');
    });
  });
  log.info('expireIncidents cron scheduled (0 * * * *)');
}

export async function expireIncidents(log: Logger): Promise<void> {
  const expireRules: Array<{ type: string; intervalSql: string }> = [
    { type: 'ACCIDENT', intervalSql: '2 hours' },
    { type: 'CONSTRUCTION', intervalSql: '8 hours' },
    { type: 'CLOSURE', intervalSql: '24 hours' },
    { type: 'POLICE', intervalSql: '1 hour' },
    { type: 'POTHOLE', intervalSql: '14 days' },
    { type: 'CONGESTION', intervalSql: '1 hour' },
  ];

  for (const rule of expireRules) {
    try {
      // Use a raw RPC to do the type-specific expiry atomically
      const { error } = await supabase.rpc('expire_incidents_by_type', {
        event_type: rule.type,
        ttl_interval: rule.intervalSql,
      });

      if (error) {
        log.error({ err: error, type: rule.type }, 'expireIncidents RPC error');
        continue;
      }

      log.debug({ type: rule.type }, 'expireIncidents: type processed');
    } catch (err: unknown) {
      log.error({ err, type: rule.type }, 'expireIncidents unexpected error');
    }
  }

  log.info({ types: expireRules.length }, 'expireIncidents job complete');
}
