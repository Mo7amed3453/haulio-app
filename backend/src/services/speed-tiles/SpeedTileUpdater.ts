import { promises as fs } from 'node:fs';
import path from 'node:path';
import cron from 'node-cron';
import type { Logger } from 'pino';
import { supabase, type ExtremeZone } from '../../lib/supabase.js';
import { env } from '../../env.js';

// ── Types ──────────────────────────────────────────────────────────────────────

interface TelemetryRow {
  lat: number;
  lng: number;
  speed_mps: number;
}

interface EdgeSpeed {
  edgeId: string;
  speedKph: number;
  sampleCount: number;
}

interface ValhallaCustomSpeedEntry {
  way_id: number;
  speed: number;
}

interface ValhallaSpeedFile {
  version: 1;
  speeds: ValhallaCustomSpeedEntry[];
}

interface HealthStatus {
  lastRunTs: string | null;
  status: 'idle' | 'running' | 'ok' | 'error';
  tilesUpdated: number;
  segmentsProcessed: number;
  tomtomCalls: number;
  durationMs: number;
}

interface TomTomFlowSegment {
  frc: string;
  currentSpeed: number;
  freeFlowSpeed: number;
  coordinates: { latitude: number; longitude: number }[];
}

interface TomTomFlowResponse {
  flowSegmentData: TomTomFlowSegment;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Snap a (lat, lng) to a coarse Valhalla tile id (1-degree grid) */
function latLngToTileId(lat: number, lng: number): string {
  const tileLat = Math.floor(lat);
  const tileLng = Math.floor(lng);
  return `${tileLat}_${tileLng}`;
}

/** Derive a pseudo-way_id from lat/lng for demo; real impl uses OSM edge ids */
function latLngToWayId(lat: number, lng: number): number {
  // Hash lat/lng to a stable integer (not cryptographic, just stable)
  const latInt = Math.round(lat * 1e5);
  const lngInt = Math.round(lng * 1e5);
  return Math.abs((latInt * 1_000_000 + lngInt) % 2_147_483_647);
}

function isWindowActive(windows: ExtremeZone['windows']): boolean {
  const now = new Date();
  const dayOfWeek = now.getDay(); // 0=Sun, 1=Mon, ..., 6=Sat
  const hhmm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

  return windows.some((w) => {
    if (!w.days.includes(dayOfWeek)) return false;
    return hhmm >= w.start && hhmm <= w.end;
  });
}

// ── Budget helpers ─────────────────────────────────────────────────────────────

const BACKEND_DAILY_LIMIT = 200;

async function getRemainingBudget(): Promise<number> {
  const today = new Date().toISOString().slice(0, 10);
  const { data, error } = await supabase
    .from('tomtom_budget')
    .select('backend_calls')
    .eq('date', today)
    .maybeSingle();

  if (error) throw new Error(`Budget fetch failed: ${error.message}`);
  const used = data?.backend_calls ?? 0;
  return Math.max(0, BACKEND_DAILY_LIMIT - used);
}

async function consumeBudget(count: number): Promise<void> {
  for (let i = 0; i < count; i++) {
    const { error } = await supabase.rpc('increment_tomtom_call', { scope: 'backend' });
    if (error) throw new Error(`Budget increment failed: ${error.message}`);
  }
}

// ── TomTom fetch ───────────────────────────────────────────────────────────────

async function fetchTomTomFlow(lat: number, lng: number): Promise<TomTomFlowResponse | null> {
  const url =
    `https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json` +
    `?point=${lat},${lng}&key=${env.TOMTOM_API_KEY}`;

  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`TomTom flow request failed: ${res.status} ${res.statusText}`);
  }

  return (await res.json()) as TomTomFlowResponse;
}

// ── SpeedTileUpdater ───────────────────────────────────────────────────────────

export class SpeedTileUpdater {
  private readonly log: Logger;
  private health: HealthStatus = {
    lastRunTs: null,
    status: 'idle',
    tilesUpdated: 0,
    segmentsProcessed: 0,
    tomtomCalls: 0,
    durationMs: 0,
  };

  constructor(log: Logger) {
    this.log = log;
  }

  getHealth(): HealthStatus {
    return { ...this.health };
  }

  startCron(): void {
    // Every 10 minutes
    cron.schedule('*/10 * * * *', () => {
      this.run().catch((err: unknown) => {
        this.log.error({ err }, 'SpeedTileUpdater cron unhandled error');
      });
    });
    this.log.info('SpeedTileUpdater cron scheduled (*/10 * * * *)');
  }

  async run(): Promise<HealthStatus> {
    const startTs = Date.now();
    this.health.status = 'running';

    let tilesUpdated = 0;
    let segmentsProcessed = 0;
    let tomtomCalls = 0;

    try {
      // ── 1. Aggregate crowd-sourced speed reports (last 10 min) ──────────────
      const tenMinAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString();
      const { data: telemetryRows, error: telErr } = await supabase
        .from('driver_telemetry')
        .select('lat, lng, speed_mps')
        .gte('ts', tenMinAgo);

      if (telErr) throw new Error(`Telemetry fetch failed: ${telErr.message}`);

      const telemetry: TelemetryRow[] = (telemetryRows ?? []) as TelemetryRow[];

      // Group by coarse tile
      const tileEdgeMap = new Map<string, Map<string, { totalKph: number; count: number }>>();

      for (const row of telemetry) {
        const tileId = latLngToTileId(row.lat, row.lng);
        const wayId = String(latLngToWayId(row.lat, row.lng));
        const speedKph = row.speed_mps * 3.6;

        if (!tileEdgeMap.has(tileId)) tileEdgeMap.set(tileId, new Map());
        const edgeMap = tileEdgeMap.get(tileId)!;
        const existing = edgeMap.get(wayId) ?? { totalKph: 0, count: 0 };
        edgeMap.set(wayId, { totalKph: existing.totalKph + speedKph, count: existing.count + 1 });
      }

      // ── 2. Fetch TomTom for active extreme zones (budget-gated) ────────────
      const { data: zones, error: zoneErr } = await supabase
        .from('extreme_zones')
        .select('id, type, geometry, windows');

      if (zoneErr) throw new Error(`Zone fetch failed: ${zoneErr.message}`);

      const activeZones = (zones ?? []) as Array<{
        id: string;
        type: string;
        geometry: { coordinates: [number, number] } | null;
        windows: ExtremeZone['windows'];
      }>;

      const currentlyActive = activeZones.filter((z) => isWindowActive(z.windows));

      let remaining = await getRemainingBudget();

      for (const zone of currentlyActive) {
        if (remaining <= 0) {
          this.log.warn({ zoneId: zone.id }, 'TomTom budget exhausted – skipping zone');
          break;
        }

        const coords = zone.geometry?.coordinates;
        if (!coords) continue;

        const [lng, lat] = coords;
        if (lat === undefined || lng === undefined) continue;

        try {
          const flow = await fetchTomTomFlow(lat, lng);
          if (!flow) continue;

          tomtomCalls++;
          remaining--;

          const { currentSpeed } = flow.flowSegmentData;
          const tileId = latLngToTileId(lat, lng);
          const wayId = String(latLngToWayId(lat, lng));

          if (!tileEdgeMap.has(tileId)) tileEdgeMap.set(tileId, new Map());
          const edgeMap = tileEdgeMap.get(tileId)!;
          const existing = edgeMap.get(wayId) ?? { totalKph: 0, count: 0 };
          // TomTom data counts as one sample
          edgeMap.set(wayId, {
            totalKph: existing.totalKph + currentSpeed,
            count: existing.count + 1,
          });
        } catch (fetchErr: unknown) {
          this.log.error({ err: fetchErr, zoneId: zone.id }, 'TomTom flow fetch error');
        }
      }

      if (tomtomCalls > 0) {
        await consumeBudget(tomtomCalls);
      }

      // ── 3. Write Valhalla .spd tiles ────────────────────────────────────────
      await fs.mkdir(env.VALHALLA_LIVE_TRAFFIC_DIR, { recursive: true });

      for (const [tileId, edgeMap] of tileEdgeMap) {
        const speeds: ValhallaCustomSpeedEntry[] = [];

        for (const [wayIdStr, { totalKph, count }] of edgeMap) {
          const avgKph = Math.round(totalKph / count);
          speeds.push({ way_id: Number(wayIdStr), speed: avgKph });
          segmentsProcessed++;
        }

        const payload: ValhallaSpeedFile = { version: 1, speeds };
        const filePath = path.join(env.VALHALLA_LIVE_TRAFFIC_DIR, `${tileId}.spd`);
        await fs.writeFile(filePath, JSON.stringify(payload), 'utf-8');
        tilesUpdated++;
      }

      // ── 4. Trigger Valhalla reload ──────────────────────────────────────────
      if (env.VALHALLA_RELOAD_URL && tilesUpdated > 0) {
        try {
          const reloadRes = await fetch(env.VALHALLA_RELOAD_URL, { method: 'GET' });
          if (!reloadRes.ok) {
            this.log.warn({ status: reloadRes.status }, 'Valhalla reload request returned non-2xx');
          }
        } catch (reloadErr: unknown) {
          this.log.warn({ err: reloadErr }, 'Valhalla reload request failed (non-fatal)');
        }
      }

      const durationMs = Date.now() - startTs;
      this.health = {
        lastRunTs: new Date().toISOString(),
        status: 'ok',
        tilesUpdated,
        segmentsProcessed,
        tomtomCalls,
        durationMs,
      };

      this.log.info({ tilesUpdated, segmentsProcessed, tomtomCalls, durationMs }, 'SpeedTileUpdater run complete');
    } catch (err: unknown) {
      const durationMs = Date.now() - startTs;
      this.health = {
        lastRunTs: new Date().toISOString(),
        status: 'error',
        tilesUpdated,
        segmentsProcessed,
        tomtomCalls,
        durationMs,
      };
      this.log.error({ err, tilesUpdated, segmentsProcessed, tomtomCalls, durationMs }, 'SpeedTileUpdater run failed');
    }

    return this.health;
  }
}
