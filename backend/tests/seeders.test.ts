import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@supabase/supabase-js', () => ({
  createClient: vi.fn(() => mockSupabase),
}));

vi.mock('../src/env.js', () => ({
  env: {
    SUPABASE_URL: 'https://test.supabase.co',
    SUPABASE_SERVICE_KEY: 'test-key',
    TOMTOM_API_KEY: 'test-tomtom-key',
    VALHALLA_LIVE_TRAFFIC_DIR: '/tmp',
    PORT: 3001,
    NODE_ENV: 'test' as const,
  },
}));

const mockUpsert = vi.fn();
const mockSupabase = {
  from: vi.fn(() => ({ upsert: mockUpsert })),
  rpc: vi.fn(),
};

// Mock fetch for Overpass API
const mockFetch = vi.spyOn(globalThis, 'fetch');

describe('seed-school-zones', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUpsert.mockResolvedValue({ error: null });
  });

  it('upserts school zones for nodes returned by Overpass', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          elements: [
            { type: 'node', id: 1, lat: 37.77, lon: -122.41, tags: { name: 'Test Elementary' } },
            { type: 'node', id: 2, lat: 37.78, lon: -122.42, tags: {} },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    const { seedSchoolZones } = await import('../src/scripts/seed-school-zones.js');
    const count = await seedSchoolZones(37.0, -123.0, 38.0, -122.0);

    expect(count).toBe(2);
    expect(mockUpsert).toHaveBeenCalledOnce();
    const [rows] = mockUpsert.mock.calls[0] as [Array<Record<string, unknown>>, unknown];
    expect(rows[0]!['type']).toBe('SCHOOL');
    expect(rows[0]!['name']).toBe('Test Elementary');
    expect(rows[0]!['priority']).toBe(2);
  });

  it('returns 0 when Overpass returns no nodes', async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ elements: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const { seedSchoolZones } = await import('../src/scripts/seed-school-zones.js');
    const count = await seedSchoolZones(37.0, -123.0, 38.0, -122.0);

    expect(count).toBe(0);
    expect(mockUpsert).not.toHaveBeenCalled();
  });
});

describe('seed-industrial-zones', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUpsert.mockResolvedValue({ error: null });
  });

  it('upserts industrial zones with centroid from center field', async () => {
    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          elements: [
            {
              type: 'way',
              id: 10,
              nodes: [1, 2, 3],
              center: { lat: 37.8, lon: -122.3 },
              tags: { name: 'Port of Oakland' },
            },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    const { seedIndustrialZones } = await import('../src/scripts/seed-industrial-zones.js');
    const count = await seedIndustrialZones(37.0, -123.0, 38.0, -122.0);

    expect(count).toBe(1);
    expect(mockUpsert).toHaveBeenCalledOnce();
    const [rows] = mockUpsert.mock.calls[0] as [Array<Record<string, unknown>>, unknown];
    expect(rows[0]!['type']).toBe('INDUSTRIAL');
    expect(rows[0]!['priority']).toBe(3);
  });
});

describe('seed-rail-crossings', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUpsert.mockResolvedValue({ error: null });
  });

  it('upserts rail crossings and attempts transit.land lookup', async () => {
    let callCount = 0;
    mockFetch.mockImplementation(async (url: string | URL | Request) => {
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.includes('overpass-api.de')) {
        return new Response(
          JSON.stringify({
            elements: [
              { type: 'node', id: 99, lat: 37.77, lon: -122.41, tags: { name: 'Level Crossing 1' } },
            ],
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      // transit.land returns empty
      return new Response(JSON.stringify({ stop_times: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    const { seedRailCrossings } = await import('../src/scripts/seed-rail-crossings.js');
    const count = await seedRailCrossings(37.0, -123.0, 38.0, -122.0);

    expect(count).toBe(1);
    const [rows] = mockUpsert.mock.calls[0] as [Array<Record<string, unknown>>, unknown];
    expect(rows[0]!['type']).toBe('RAIL_CROSSING');
    expect(rows[0]!['priority']).toBe(1);
  });
});

describe('seed-historical-corridors', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUpsert.mockResolvedValue({ error: null });
  });

  it('upserts historical corridors returned by RPC', async () => {
    mockSupabase.rpc.mockResolvedValue({
      data: [
        {
          tile_id: '37_-122',
          centroid_lat: 37.5,
          centroid_lng: -122.5,
          day_of_week: 1,
          hour_start: 17,
          hour_end: 18,
          avg_speed_ratio: 0.22,
          week_count: 4,
        },
      ],
      error: null,
    });

    const { seedHistoricalCorridors } = await import('../src/scripts/seed-historical-corridors.js');
    const count = await seedHistoricalCorridors();

    expect(count).toBe(1);
    const [rows] = mockUpsert.mock.calls[0] as [Array<Record<string, unknown>>, unknown];
    expect(rows[0]!['type']).toBe('HISTORICAL_CORRIDOR');
    expect(rows[0]!['priority']).toBe(4);
  });

  it('returns 0 when RPC returns empty array', async () => {
    mockSupabase.rpc.mockResolvedValue({ data: [], error: null });

    const { seedHistoricalCorridors } = await import('../src/scripts/seed-historical-corridors.js');
    const count = await seedHistoricalCorridors();

    expect(count).toBe(0);
    expect(mockUpsert).not.toHaveBeenCalled();
  });
});
