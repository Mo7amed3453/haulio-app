import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import pino from 'pino';

// ── Mocks ─────────────────────────────────────────────────────────────────────

// Mock @supabase/supabase-js before importing SpeedTileUpdater
vi.mock('@supabase/supabase-js', () => ({
  createClient: vi.fn(() => mockSupabase),
}));

// Mock env module
vi.mock('../src/env.js', () => ({
  env: {
    SUPABASE_URL: 'https://test.supabase.co',
    SUPABASE_SERVICE_KEY: 'test-key',
    TOMTOM_API_KEY: 'test-tomtom-key',
    VALHALLA_LIVE_TRAFFIC_DIR: '',  // set per-test via spying
    VALHALLA_RELOAD_URL: 'http://localhost:8002/reload',
    PORT: 3001,
    NODE_ENV: 'test' as const,
  },
}));

const mockSupabaseFrom = vi.fn();
const mockRpc = vi.fn();

const mockSupabase = {
  from: mockSupabaseFrom,
  rpc: mockRpc,
};

// ── Test helpers ──────────────────────────────────────────────────────────────

function makeTelemetrySelect(rows: Array<{ lat: number; lng: number; speed_mps: number }>) {
  return {
    select: vi.fn().mockReturnThis(),
    gte: vi.fn().mockReturnThis(),
    data: rows,
    error: null,
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SpeedTileUpdater', () => {
  let tmpDir: string;
  const log = pino({ level: 'silent' });

  beforeEach(async () => {
    tmpDir = await fs.mkdtemp(path.join(os.tmpdir(), 'haulio-test-'));
    vi.resetAllMocks();
  });

  afterEach(async () => {
    await fs.rm(tmpDir, { recursive: true, force: true });
    vi.restoreAllMocks();
  });

  it('writes a .spd tile file for crowd-sourced telemetry', async () => {
    // Arrange
    const telemetryRows = [
      { lat: 37.77, lng: -122.41, speed_mps: 13.9 }, // ~50 kph
      { lat: 37.77, lng: -122.41, speed_mps: 11.1 }, // ~40 kph
    ];

    mockSupabaseFrom.mockImplementation((table: string) => {
      if (table === 'driver_telemetry') {
        return {
          select: vi.fn().mockReturnThis(),
          gte: vi.fn().mockResolvedValue({ data: telemetryRows, error: null }),
        };
      }
      if (table === 'extreme_zones') {
        return {
          select: vi.fn().mockResolvedValue({ data: [], error: null }),
        };
      }
      if (table === 'tomtom_budget') {
        return {
          select: vi.fn().mockReturnThis(),
          eq: vi.fn().mockReturnThis(),
          maybeSingle: vi.fn().mockResolvedValue({ data: { backend_calls: 0 }, error: null }),
        };
      }
      return {};
    });

    // Patch env.VALHALLA_LIVE_TRAFFIC_DIR to our tmpDir
    const envMod = await import('../src/env.js');
    (envMod.env as Record<string, unknown>)['VALHALLA_LIVE_TRAFFIC_DIR'] = tmpDir;

    const { SpeedTileUpdater } = await import('../src/services/speed-tiles/SpeedTileUpdater.js');
    const updater = new SpeedTileUpdater(log);

    // Act
    const health = await updater.run();

    // Assert: status ok
    expect(health.status).toBe('ok');
    expect(health.segmentsProcessed).toBeGreaterThan(0);
    expect(health.tilesUpdated).toBeGreaterThan(0);

    // Assert: .spd file written
    const files = await fs.readdir(tmpDir);
    expect(files.some((f) => f.endsWith('.spd'))).toBe(true);

    const spdContent = JSON.parse(await fs.readFile(path.join(tmpDir, files[0]!), 'utf-8')) as {
      version: number;
      speeds: Array<{ way_id: number; speed: number }>;
    };
    expect(spdContent.version).toBe(1);
    expect(spdContent.speeds.length).toBeGreaterThan(0);
    expect(spdContent.speeds[0]!.speed).toBeGreaterThan(0);
  });

  it('skips TomTom calls when budget is exhausted', async () => {
    const telemetryRows: unknown[] = [];

    mockSupabaseFrom.mockImplementation((table: string) => {
      if (table === 'driver_telemetry') {
        return {
          select: vi.fn().mockReturnThis(),
          gte: vi.fn().mockResolvedValue({ data: telemetryRows, error: null }),
        };
      }
      if (table === 'extreme_zones') {
        // Return an active zone
        return {
          select: vi.fn().mockResolvedValue({
            data: [{
              id: 'zone-1',
              type: 'SCHOOL',
              geometry: { coordinates: [-122.41, 37.77] },
              windows: [{ days: [0, 1, 2, 3, 4, 5, 6], start: '00:00', end: '23:59' }],
            }],
            error: null,
          }),
        };
      }
      if (table === 'tomtom_budget') {
        return {
          select: vi.fn().mockReturnThis(),
          eq: vi.fn().mockReturnThis(),
          // Budget fully exhausted
          maybeSingle: vi.fn().mockResolvedValue({ data: { backend_calls: 200 }, error: null }),
        };
      }
      return {};
    });

    const envMod = await import('../src/env.js');
    (envMod.env as Record<string, unknown>)['VALHALLA_LIVE_TRAFFIC_DIR'] = tmpDir;

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 200 }));

    const { SpeedTileUpdater } = await import('../src/services/speed-tiles/SpeedTileUpdater.js');
    const updater = new SpeedTileUpdater(log);
    const health = await updater.run();

    // TomTom should NOT have been called
    expect(health.tomtomCalls).toBe(0);
    fetchSpy.mockRestore();
  });

  it('does not crash when telemetry fetch fails', async () => {
    mockSupabaseFrom.mockImplementation((table: string) => {
      if (table === 'driver_telemetry') {
        return {
          select: vi.fn().mockReturnThis(),
          gte: vi.fn().mockResolvedValue({ data: null, error: { message: 'DB error' } }),
        };
      }
      return {};
    });

    const envMod = await import('../src/env.js');
    (envMod.env as Record<string, unknown>)['VALHALLA_LIVE_TRAFFIC_DIR'] = tmpDir;

    const { SpeedTileUpdater } = await import('../src/services/speed-tiles/SpeedTileUpdater.js');
    const updater = new SpeedTileUpdater(log);
    const health = await updater.run();

    // Should not throw; status should be 'error'
    expect(health.status).toBe('error');
  });
});
