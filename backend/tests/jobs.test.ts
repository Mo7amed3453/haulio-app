import { describe, it, expect, vi, beforeEach } from 'vitest';
import pino from 'pino';

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

const mockRpc = vi.fn();
const mockSupabase = {
  from: vi.fn(),
  rpc: mockRpc,
};

describe('expireIncidents', () => {
  const log = pino({ level: 'silent' });

  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('calls expire_incidents_by_type for each event type', async () => {
    mockRpc.mockResolvedValue({ data: null, error: null });

    const { expireIncidents } = await import('../src/jobs/expireIncidents.js');
    await expireIncidents(log);

    // Should be called once per type (6 types)
    expect(mockRpc).toHaveBeenCalledTimes(6);

    const calls = mockRpc.mock.calls as Array<[string, Record<string, unknown>]>;
    const types = calls.map(([_fn, args]) => args['event_type'] as string);
    expect(types).toContain('ACCIDENT');
    expect(types).toContain('POTHOLE');
    expect(types).toContain('CONGESTION');
  });

  it('does not throw when RPC returns an error', async () => {
    mockRpc.mockResolvedValue({ data: null, error: { message: 'RPC error' } });

    const { expireIncidents } = await import('../src/jobs/expireIncidents.js');
    // Should not throw
    await expect(expireIncidents(log)).resolves.not.toThrow();
  });
});

describe('runConfirmationAggregator', () => {
  const log = pino({ level: 'silent' });

  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('returns count of verified incidents', async () => {
    mockRpc.mockResolvedValue({ data: 3, error: null });

    const { runConfirmationAggregator } = await import('../src/jobs/confirmationAggregator.js');
    const count = await runConfirmationAggregator(log);

    expect(count).toBe(3);
    expect(mockRpc).toHaveBeenCalledWith('aggregate_incident_confirmations', {
      radius_meters: 200,
      window_minutes: 30,
      threshold: 3,
    });
  });

  it('returns 0 when RPC fails', async () => {
    mockRpc.mockResolvedValue({ data: null, error: { message: 'fail' } });

    const { runConfirmationAggregator } = await import('../src/jobs/confirmationAggregator.js');
    const count = await runConfirmationAggregator(log);

    expect(count).toBe(0);
  });
});
