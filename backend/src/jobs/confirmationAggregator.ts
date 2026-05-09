import cron from 'node-cron';
import type { Logger } from 'pino';
import { supabase } from '../lib/supabase.js';

const CONFIRMATION_RADIUS_METERS = 200;
const CONFIRMATION_WINDOW_MINUTES = 30;
const CONFIRMATION_THRESHOLD = 3;

/**
 * Every 5 minutes: check if 3+ drivers reported the same incident type
 * within 200 m and 30 min of each other → mark verified = true.
 */
export function startConfirmationAggregator(log: Logger): void {
  cron.schedule('*/5 * * * *', () => {
    runConfirmationAggregator(log).catch((err: unknown) => {
      log.error({ err }, 'confirmationAggregator cron unhandled error');
    });
  });
  log.info('confirmationAggregator cron scheduled (*/5 * * * *)');
}

export async function runConfirmationAggregator(log: Logger): Promise<number> {
  let verifiedCount = 0;

  try {
    // Delegate spatial aggregation to a Postgres function for correctness + performance
    const { data, error } = await supabase.rpc('aggregate_incident_confirmations', {
      radius_meters: CONFIRMATION_RADIUS_METERS,
      window_minutes: CONFIRMATION_WINDOW_MINUTES,
      threshold: CONFIRMATION_THRESHOLD,
    });

    if (error) {
      log.error({ err: error }, 'confirmationAggregator RPC error');
      return 0;
    }

    verifiedCount = (data as number | null) ?? 0;
    log.info({ verifiedCount }, 'confirmationAggregator run complete');
  } catch (err: unknown) {
    log.error({ err }, 'confirmationAggregator unexpected error');
  }

  return verifiedCount;
}
