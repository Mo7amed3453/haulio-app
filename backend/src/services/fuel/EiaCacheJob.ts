import cron from 'node-cron';
import type { Logger } from 'pino';
import { supabase } from '../../lib/supabase.js';

// ---------------------------------------------------------------------------
// PADD district codes fetched every hour
// ---------------------------------------------------------------------------

const PADD_DISTRICTS: readonly string[] = [
  'R1X', // PADD 1A — New England
  'R1Y', // PADD 1B — Central Atlantic
  'R1Z', // PADD 1C — Lower Atlantic
  'R20', // PADD 2  — Midwest
  'R30', // PADD 3  — Gulf Coast
  'R40', // PADD 4  — Rocky Mountain
  'R50', // PADD 5  — West Coast
];

const EIA_BASE_URL = 'https://api.eia.gov/v2/petroleum/pri/gnd/data/';
const PRODUCT_REGULAR = 'EPM0';

interface EiaDataPoint {
  period: string;
  duoarea: string;
  value: number;
}

interface EiaApiResponse {
  response: {
    data: EiaDataPoint[];
  };
}

// ---------------------------------------------------------------------------
// Exported functions
// ---------------------------------------------------------------------------

/**
 * Starts an hourly cron job that fetches EIA prices for all PADD districts
 * and upserts them into the `fuel_eia_cache` table.
 */
export function startEiaCacheJob(log: Logger): void {
  cron.schedule('0 * * * *', () => {
    runEiaCacheJob(log).catch((err: unknown) => {
      log.error({ err }, 'EiaCacheJob cron unhandled error');
    });
  });
  log.info('EiaCacheJob cron scheduled (0 * * * *)');
}

/**
 * Fetches EIA weekly regular gasoline prices for all PADD districts and
 * upserts them into `fuel_eia_cache`.
 *
 * @returns The number of districts successfully updated.
 */
export async function runEiaCacheJob(log: Logger): Promise<number> {
  let updated = 0;

  for (const district of PADD_DISTRICTS) {
    try {
      const point = await fetchEiaPrice(district);
      if (!point) {
        log.warn({ district }, 'EiaCacheJob: no data returned for district');
        continue;
      }

      const { error } = await supabase
        .from('fuel_eia_cache')
        .upsert(
          {
            district,
            regular:    point.value,
            week_of:    point.period,
            fetched_at: new Date().toISOString(),
          },
          { onConflict: 'district' },
        );

      if (error) {
        log.error({ err: error, district }, 'EiaCacheJob: upsert error');
        continue;
      }

      updated++;
      log.debug({ district, value: point.value, period: point.period }, 'EiaCacheJob: upserted');
    } catch (err: unknown) {
      log.error({ err, district }, 'EiaCacheJob: unexpected error');
    }
  }

  log.info({ updated, total: PADD_DISTRICTS.length }, 'EiaCacheJob complete');
  return updated;
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

async function fetchEiaPrice(district: string): Promise<EiaDataPoint | null> {
  const url = new URL(EIA_BASE_URL);
  url.searchParams.set('frequency', 'weekly');
  url.searchParams.set('data[0]', 'value');
  url.searchParams.set('facets[duoarea][]', district);
  url.searchParams.set('facets[product][]', PRODUCT_REGULAR);
  url.searchParams.set('sort[0][column]', 'period');
  url.searchParams.set('sort[0][direction]', 'desc');
  url.searchParams.set('offset', '0');
  url.searchParams.set('length', '1');

  const response = await fetch(url.toString());
  if (!response.ok) {
    throw new Error(`EIA API returned HTTP ${response.status} for district ${district}`);
  }

  const body = (await response.json()) as EiaApiResponse;
  return body.response?.data?.[0] ?? null;
}
