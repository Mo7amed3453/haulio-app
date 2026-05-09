import cron from 'node-cron';
import type { Logger } from 'pino';
import { supabase } from '../../lib/supabase.js';

// ---------------------------------------------------------------------------
// City API configuration
// ---------------------------------------------------------------------------

const NINETY_DAYS_MS = 90 * 24 * 60 * 60 * 1000;

interface NormalizedIncident {
  type: string;
  severity: number;
  lat: number;
  lng: number;
  occurred_at: string;
  source: string;
  source_id: string;
}

// ---------------------------------------------------------------------------
// Severity weight lookup
// ---------------------------------------------------------------------------

function severityForType(type: string): number {
  const t = type.toLowerCase();
  if (t.includes('robbery'))                            return 10;
  if (t.includes('assault') || t.includes('aggravated')) return 8;
  if (t.includes('burglar'))                            return 6;
  if (t.includes('theft') || t.includes('larceny'))    return 4;
  if (t.includes('vehicle') || t.includes('motor') || t.includes('auto')) return 3;
  return 1;
}

// ---------------------------------------------------------------------------
// NYC: NYPD Complaint Data — last 30 days
// ---------------------------------------------------------------------------

interface NycRow {
  cmplnt_num: string;
  ofns_desc: string;
  cmplnt_fr_dt: string;       // "MM/DD/YYYY"
  latitude: string;
  longitude: string;
}

async function fetchNycIncidents(log: Logger): Promise<NormalizedIncident[]> {
  const since = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
  const sinceStr = `${String(since.getMonth() + 1).padStart(2, '0')}/${String(since.getDate()).padStart(2, '0')}/${since.getFullYear()}`;

  const url = new URL('https://data.cityofnewyork.us/resource/qgea-i56i.json');
  url.searchParams.set('$where', `cmplnt_fr_dt >= '${sinceStr}'`);
  url.searchParams.set('$limit', '1000');
  url.searchParams.set('$select', 'cmplnt_num,ofns_desc,cmplnt_fr_dt,latitude,longitude');

  const resp = await fetch(url.toString(), {
    headers: { 'Accept': 'application/json' },
  });
  if (!resp.ok) {
    throw new Error(`NYC API returned HTTP ${resp.status}`);
  }

  const rows = (await resp.json()) as NycRow[];
  const results: NormalizedIncident[] = [];

  for (const row of rows) {
    const lat = parseFloat(row.latitude);
    const lng = parseFloat(row.longitude);
    if (!isFinite(lat) || !isFinite(lng)) continue;
    if (!row.cmplnt_num || !row.ofns_desc) continue;

    const type = row.ofns_desc.trim();
    results.push({
      type,
      severity: severityForType(type),
      lat,
      lng,
      occurred_at: parseNycDate(row.cmplnt_fr_dt),
      source: 'nypd',
      source_id: `nypd_${row.cmplnt_num}`,
    });
  }

  return results;
}

function parseNycDate(mmddyyyy: string): string {
  try {
    const [mm, dd, yyyy] = mmddyyyy.split('/');
    return new Date(`${yyyy}-${mm}-${dd}T00:00:00Z`).toISOString();
  } catch {
    return new Date().toISOString();
  }
}

// ---------------------------------------------------------------------------
// LA: LAPD Crime Data — last 30 days
// ---------------------------------------------------------------------------

interface LaRow {
  dr_no: string;
  crm_cd_desc: string;
  date_occ: string;   // "MM/DD/YYYY hh:mm:ss AM"
  lat: string;
  lon: string;
}

async function fetchLaIncidents(log: Logger): Promise<NormalizedIncident[]> {
  const since = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
  const sinceStr = `${String(since.getMonth() + 1).padStart(2, '0')}/${String(since.getDate()).padStart(2, '0')}/${since.getFullYear()}`;

  const url = new URL('https://data.lacity.org/resource/2nrs-mtv8.json');
  url.searchParams.set('$where', `date_occ >= '${sinceStr}'`);
  url.searchParams.set('$limit', '1000');
  url.searchParams.set('$select', 'dr_no,crm_cd_desc,date_occ,lat,lon');

  const resp = await fetch(url.toString(), {
    headers: { 'Accept': 'application/json' },
  });
  if (!resp.ok) {
    throw new Error(`LA API returned HTTP ${resp.status}`);
  }

  const rows = (await resp.json()) as LaRow[];
  const results: NormalizedIncident[] = [];

  for (const row of rows) {
    const lat = parseFloat(row.lat);
    const lng = parseFloat(row.lon);
    if (!isFinite(lat) || !isFinite(lng)) continue;
    if (!row.dr_no || !row.crm_cd_desc) continue;

    const type = row.crm_cd_desc.trim();
    results.push({
      type,
      severity: severityForType(type),
      lat,
      lng,
      occurred_at: parseLaDate(row.date_occ),
      source: 'lapd',
      source_id: `lapd_${row.dr_no}`,
    });
  }

  return results;
}

function parseLaDate(raw: string): string {
  try {
    return new Date(raw).toISOString();
  } catch {
    return new Date().toISOString();
  }
}

// ---------------------------------------------------------------------------
// Chicago: Chicago PD Crime Data — last 30 days
// ---------------------------------------------------------------------------

interface ChicagoRow {
  id: string;
  primary_type: string;
  date: string;    // ISO string
  latitude: string;
  longitude: string;
}

async function fetchChicagoIncidents(log: Logger): Promise<NormalizedIncident[]> {
  const since = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();

  const url = new URL('https://data.cityofchicago.org/resource/ijzp-q8t2.json');
  url.searchParams.set('$where', `date >= '${since}'`);
  url.searchParams.set('$limit', '1000');
  url.searchParams.set('$select', 'id,primary_type,date,latitude,longitude');

  const resp = await fetch(url.toString(), {
    headers: { 'Accept': 'application/json' },
  });
  if (!resp.ok) {
    throw new Error(`Chicago API returned HTTP ${resp.status}`);
  }

  const rows = (await resp.json()) as ChicagoRow[];
  const results: NormalizedIncident[] = [];

  for (const row of rows) {
    const lat = parseFloat(row.latitude);
    const lng = parseFloat(row.longitude);
    if (!isFinite(lat) || !isFinite(lng)) continue;
    if (!row.id || !row.primary_type) continue;

    const type = row.primary_type.trim();
    results.push({
      type,
      severity: severityForType(type),
      lat,
      lng,
      occurred_at: new Date(row.date).toISOString(),
      source: 'chicago_pd',
      source_id: `chicago_${row.id}`,
    });
  }

  return results;
}

// ---------------------------------------------------------------------------
// FBI Crime Data API (NIBRS — summary endpoints, no key required)
// Fetches national offense summary for major metro UCR codes.
// ---------------------------------------------------------------------------

interface FbiOffenseRow {
  data_year: number;
  offense: string;
  state_abbr: string;
  actual: number;
}

interface FbiFetchResult {
  results: FbiOffenseRow[];
}

// UCR offense codes we care about for the heatmap
const FBI_OFFENSES = ['aggravated-assault', 'burglary', 'larceny', 'motor-vehicle-theft', 'robbery'];

/**
 * Fetches FBI NIBRS summary data and synthesises synthetic metro-level
 * incidents. These are aggregates (not precise coordinates) so we centre
 * each incident at the approximate city centroid and spread them with a
 * small jitter. This gives national coverage until city-level APIs cover
 * every metro.
 */
async function fetchFbiIncidents(log: Logger): Promise<NormalizedIncident[]> {
  const year = new Date().getFullYear() - 1; // NIBRS data lags ~1 year
  const results: NormalizedIncident[] = [];

  // Major metro centroids: [lat, lng, stateAbbr]
  const metros: Array<{ name: string; lat: number; lng: number; state: string }> = [
    { name: 'Houston',      lat: 29.7604, lng: -95.3698, state: 'TX' },
    { name: 'Phoenix',      lat: 33.4484, lng: -112.0740, state: 'AZ' },
    { name: 'Philadelphia', lat: 39.9526, lng: -75.1652,  state: 'PA' },
    { name: 'San Antonio',  lat: 29.4241, lng: -98.4936,  state: 'TX' },
    { name: 'Dallas',       lat: 32.7767, lng: -96.7970,  state: 'TX' },
    { name: 'San Diego',    lat: 32.7157, lng: -117.1611, state: 'CA' },
    { name: 'Jacksonville', lat: 30.3322, lng: -81.6557,  state: 'FL' },
    { name: 'Austin',       lat: 30.2672, lng: -97.7431,  state: 'TX' },
    { name: 'Fort Worth',   lat: 32.7555, lng: -97.3308,  state: 'TX' },
    { name: 'Columbus',     lat: 39.9612, lng: -82.9988,  state: 'OH' },
  ];

  for (const offense of FBI_OFFENSES) {
    try {
      const url = `https://api.usa.gov/crime/fbi/cde/offenses/national/${offense}/count?from=${year}&to=${year}`;
      const resp = await fetch(url, { headers: { 'Accept': 'application/json' } });
      if (!resp.ok) {
        log.warn({ offense, status: resp.status }, 'CrimeIngester: FBI API non-OK response');
        continue;
      }

      const body = (await resp.json()) as FbiFetchResult;
      if (!body.results || body.results.length === 0) continue;

      // Distribute summary count across metros as approximate incident points
      const total = body.results.reduce((sum, r) => sum + (r.actual ?? 0), 0);
      const perMetro = Math.ceil(total / metros.length / 100); // sample — not 1:1

      for (const metro of metros) {
        for (let i = 0; i < Math.min(perMetro, 5); i++) {
          const jitterLat = (Math.random() - 0.5) * 0.05;
          const jitterLng = (Math.random() - 0.5) * 0.05;
          const sourceId = `fbi_${offense}_${metro.name.toLowerCase().replace(/\s+/g, '_')}_${year}_${i}`;
          results.push({
            type: offense.replace(/-/g, ' '),
            severity: severityForType(offense),
            lat: metro.lat + jitterLat,
            lng: metro.lng + jitterLng,
            occurred_at: new Date(`${year}-06-01T00:00:00Z`).toISOString(),
            source: 'fbi_nibrs',
            source_id: sourceId,
          });
        }
      }
    } catch (err: unknown) {
      log.warn({ err, offense }, 'CrimeIngester: FBI fetch error for offense, skipping');
    }
  }

  return results;
}

// ---------------------------------------------------------------------------
// Ingest to Supabase (idempotent via source_id UNIQUE constraint)
// ---------------------------------------------------------------------------

async function upsertIncidents(
  incidents: NormalizedIncident[],
  log: Logger,
): Promise<number> {
  if (incidents.length === 0) return 0;

  let upserted = 0;

  // Batch in chunks of 200 to stay within Supabase row limits
  const BATCH = 200;
  for (let i = 0; i < incidents.length; i += BATCH) {
    const chunk = incidents.slice(i, i + BATCH);
    const { error } = await supabase
      .from('crime_incidents')
      .upsert(chunk, { onConflict: 'source_id', ignoreDuplicates: false });

    if (error) {
      log.error({ err: error }, 'CrimeIngester: upsert batch error');
      continue;
    }
    upserted += chunk.length;
  }

  return upserted;
}

// ---------------------------------------------------------------------------
// Main ingestion run
// ---------------------------------------------------------------------------

export async function runCrimeIngester(log: Logger): Promise<number> {
  log.info('CrimeIngester: starting ingest run');

  const allIncidents: NormalizedIncident[] = [];

  const fetchers: Array<[string, (l: Logger) => Promise<NormalizedIncident[]>]> = [
    ['NYC (NYPD)',       fetchNycIncidents],
    ['LA (LAPD)',        fetchLaIncidents],
    ['Chicago PD',      fetchChicagoIncidents],
    ['FBI NIBRS',       fetchFbiIncidents],
  ];

  for (const [name, fetcher] of fetchers) {
    try {
      const incidents = await fetcher(log);
      log.info({ source: name, count: incidents.length }, 'CrimeIngester: fetched');
      allIncidents.push(...incidents);
    } catch (err: unknown) {
      log.error({ err, source: name }, 'CrimeIngester: fetch error, continuing with other sources');
    }
  }

  const upserted = await upsertIncidents(allIncidents, log);
  log.info({ upserted, total: allIncidents.length }, 'CrimeIngester: upsert complete');

  // Rebuild the heatmap grid
  try {
    const { error } = await supabase.rpc('aggregate_crime_grid');
    if (error) {
      log.error({ err: error }, 'CrimeIngester: aggregate_crime_grid RPC error');
    } else {
      log.info('CrimeIngester: aggregate_crime_grid completed');
    }
  } catch (err: unknown) {
    log.error({ err }, 'CrimeIngester: aggregate_crime_grid unexpected error');
  }

  return upserted;
}

// ---------------------------------------------------------------------------
// Cron: daily at 03:00 UTC
// ---------------------------------------------------------------------------

export function startCrimeIngester(log: Logger): void {
  cron.schedule('0 3 * * *', () => {
    runCrimeIngester(log).catch((err: unknown) => {
      log.error({ err }, 'CrimeIngester cron unhandled error');
    });
  });
  log.info('CrimeIngester cron scheduled (0 3 * * *)');
}
