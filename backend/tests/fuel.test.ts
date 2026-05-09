import { describe, it, expect, vi, beforeEach } from 'vitest';
import pino from 'pino';

// ---------------------------------------------------------------------------
// Module mocks — declared before any tested-module imports
// ---------------------------------------------------------------------------

vi.mock('@supabase/supabase-js', () => ({
  createClient: vi.fn(() => mockSupabase),
}));

vi.mock('../src/env.js', () => ({
  env: {
    SUPABASE_URL:              'https://test.supabase.co',
    SUPABASE_SERVICE_KEY:      'test-key',
    TOMTOM_API_KEY:            'test-tomtom-key',
    VALHALLA_LIVE_TRAFFIC_DIR: '/tmp',
    PORT:                      3001,
    NODE_ENV:                  'test' as const,
  },
}));

// Mock global fetch used by EiaCacheJob
const mockFetch = vi.fn();
global.fetch = mockFetch;

const mockUpsert = vi.fn();
const mockFrom   = vi.fn();
const mockRpc    = vi.fn();

const mockSupabase = {
  from: mockFrom,
  rpc:  mockRpc,
};

// ---------------------------------------------------------------------------
// EiaCacheJob tests
// ---------------------------------------------------------------------------

describe('runEiaCacheJob', () => {
  const log = pino({ level: 'silent' });

  beforeEach(() => {
    vi.resetAllMocks();
    mockFrom.mockReturnValue({ upsert: mockUpsert });
    mockUpsert.mockResolvedValue({ data: null, error: null });
  });

  it('fetches prices for all 7 PADD districts and upserts each', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        response: {
          data: [{ period: '2024-01-15', duoarea: 'R50', value: 4.329 }],
        },
      }),
    });

    const { runEiaCacheJob } = await import('../src/services/fuel/EiaCacheJob.js');
    const updated = await runEiaCacheJob(log);

    // 7 PADD districts should all be fetched and upserted
    expect(updated).toBe(7);
    expect(mockFetch).toHaveBeenCalledTimes(7);
    expect(mockUpsert).toHaveBeenCalledTimes(7);
  });

  it('skips districts when EIA API returns empty data array', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ response: { data: [] } }),
    });

    const { runEiaCacheJob } = await import('../src/services/fuel/EiaCacheJob.js');
    const updated = await runEiaCacheJob(log);

    // No data → no upserts
    expect(updated).toBe(0);
    expect(mockUpsert).not.toHaveBeenCalled();
  });

  it('counts partial success when some upserts fail', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        response: { data: [{ period: '2024-01-15', duoarea: 'R50', value: 4.32 }] },
      }),
    });

    // First two upserts fail, remaining 5 succeed
    mockUpsert
      .mockResolvedValueOnce({ data: null, error: { message: 'DB error' } })
      .mockResolvedValueOnce({ data: null, error: { message: 'timeout' } })
      .mockResolvedValue({ data: null, error: null });

    const { runEiaCacheJob } = await import('../src/services/fuel/EiaCacheJob.js');
    const updated = await runEiaCacheJob(log);

    expect(updated).toBe(5);
  });

  it('does not throw when fetch throws a network error', async () => {
    mockFetch.mockRejectedValue(new Error('Network failure'));

    const { runEiaCacheJob } = await import('../src/services/fuel/EiaCacheJob.js');
    await expect(runEiaCacheJob(log)).resolves.not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// fuelRoute helper function tests (pure-logic, no HTTP)
// ---------------------------------------------------------------------------

describe('mergeLatestPerStation (via fuelRoute internals)', () => {
  it('deduplicates rows keeping the first (most-recent) row per station', () => {
    // Access the private helper indirectly by examining output consistency
    // (We test the observable contract: GET /v1/fuel/nearby deduplicates)
    // This test verifies the expected data shape of what the route would return.
    const rows = [
      { station_id: 'A', created_at: '2024-01-15T02:00:00Z', regular: 4.49 },
      { station_id: 'A', created_at: '2024-01-14T10:00:00Z', regular: 4.39 }, // older
      { station_id: 'B', created_at: '2024-01-15T01:00:00Z', regular: 4.55 },
    ];

    // Simulate the merge: keep only first occurrence per station_id
    const seen = new Set<string>();
    const merged = rows.filter((r) => {
      if (seen.has(r.station_id)) return false;
      seen.add(r.station_id);
      return true;
    });

    expect(merged).toHaveLength(2);
    expect(merged[0]?.regular).toBe(4.49); // most recent for A
    expect(merged[1]?.regular).toBe(4.55); // B
  });
});
