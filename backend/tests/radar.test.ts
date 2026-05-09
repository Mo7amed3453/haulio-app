import { describe, it, expect, vi, beforeEach } from 'vitest';

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

// ---------------------------------------------------------------------------
// Supabase mock scaffolding
// ---------------------------------------------------------------------------

const mockInsert = vi.fn();
const mockSelect = vi.fn();
const mockRpc    = vi.fn();
const mockFrom   = vi.fn();

const mockSupabase = {
  from: mockFrom,
  rpc:  mockRpc,
};

// ---------------------------------------------------------------------------
// Build a minimal Fastify server for HTTP-level tests
// ---------------------------------------------------------------------------

async function buildServer() {
  const { server } = await import('../src/index.js');
  return server;
}

// Encode a dummy JWT with the given sub claim
function fakeJwt(sub: string): string {
  const header  = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ sub })).toString('base64url');
  return `${header}.${payload}.fakesig`;
}

// ---------------------------------------------------------------------------
// Tests: POST /v1/radar/cameras (submit)
// ---------------------------------------------------------------------------

describe('POST /v1/radar/cameras', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    // Default mock chain: from().insert().select().single()
    mockFrom.mockReturnValue({
      insert: vi.fn().mockReturnValue({
        select: vi.fn().mockReturnValue({
          single: vi.fn().mockResolvedValue({
            data:  { id: 'abc-uuid', lat: 37.7749, lng: -122.4194 },
            error: null,
          }),
        }),
      }),
      select: vi.fn().mockReturnValue({
        order:  vi.fn().mockReturnValue({ limit: vi.fn().mockResolvedValue({ data: [], error: null }) }),
      }),
    });
  });

  it('returns 401 when Authorization header is missing', async () => {
    const app = await buildServer();
    const res = await app.inject({
      method:  'POST',
      url:     '/v1/radar/cameras',
      payload: { lat: 37.7749, lng: -122.4194 },
    });
    expect(res.statusCode).toBe(401);
  });

  it('returns 400 when body is invalid (missing lat/lng)', async () => {
    const app = await buildServer();
    const res = await app.inject({
      method:  'POST',
      url:     '/v1/radar/cameras',
      headers: { Authorization: `Bearer ${fakeJwt('driver-1')}` },
      payload: { postedSpeedMph: 35 }, // missing lat/lng
    });
    expect(res.statusCode).toBe(400);
  });

  it('returns 201 on successful camera submission', async () => {
    const app = await buildServer();
    const res = await app.inject({
      method:  'POST',
      url:     '/v1/radar/cameras',
      headers: {
        Authorization:  `Bearer ${fakeJwt('driver-1')}`,
        'Content-Type': 'application/json',
      },
      payload: { lat: 37.7749, lng: -122.4194, postedSpeedMph: 35 },
    });
    expect(res.statusCode).toBe(201);
  });

  it('enforces rate limit after 10 submissions in 1 hour', async () => {
    const app = await buildServer();
    const driverId = `rate-limited-driver-${Date.now()}`;
    const jwt = fakeJwt(driverId);

    // Make 10 successful submissions
    for (let i = 0; i < 10; i++) {
      await app.inject({
        method:  'POST',
        url:     '/v1/radar/cameras',
        headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
        payload: { lat: 37.7749, lng: -122.4194 },
      });
    }

    // 11th should be rate-limited
    const res = await app.inject({
      method:  'POST',
      url:     '/v1/radar/cameras',
      headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
      payload: { lat: 37.7749, lng: -122.4194 },
    });
    expect(res.statusCode).toBe(429);
  });
});

// ---------------------------------------------------------------------------
// Tests: POST /v1/radar/cameras/:id/confirm
// ---------------------------------------------------------------------------

describe('POST /v1/radar/cameras/:id/confirm', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('returns 401 when Authorization header is missing', async () => {
    const app = await buildServer();
    const res = await app.inject({
      method: 'POST',
      url:    '/v1/radar/cameras/00000000-0000-0000-0000-000000000000/confirm',
    });
    expect(res.statusCode).toBe(401);
  });

  it('returns 200 when RPC confirm_camera succeeds', async () => {
    mockRpc.mockResolvedValue({ data: null, error: null });

    const app = await buildServer();
    const res = await app.inject({
      method:  'POST',
      url:     '/v1/radar/cameras/00000000-0000-0000-0000-000000000001/confirm',
      headers: { Authorization: `Bearer ${fakeJwt('driver-2')}` },
    });
    expect(res.statusCode).toBe(200);
    expect(JSON.parse(res.body)).toMatchObject({ ok: true });
    expect(mockRpc).toHaveBeenCalledWith('confirm_camera', {
      camera_id: '00000000-0000-0000-0000-000000000001',
    });
  });

  it('returns 404 when camera is not found', async () => {
    mockRpc.mockResolvedValue({ data: null, error: { message: 'Camera not found: xyz' } });

    const app = await buildServer();
    const res = await app.inject({
      method:  'POST',
      url:     '/v1/radar/cameras/00000000-0000-0000-0000-000000000002/confirm',
      headers: { Authorization: `Bearer ${fakeJwt('driver-3')}` },
    });
    expect(res.statusCode).toBe(404);
  });
});

// ---------------------------------------------------------------------------
// Tests: GET /v1/radar/cameras?bbox=...
// ---------------------------------------------------------------------------

describe('GET /v1/radar/cameras', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('returns 401 when Authorization header is missing', async () => {
    const app = await buildServer();
    const res = await app.inject({
      method: 'GET',
      url:    '/v1/radar/cameras',
    });
    expect(res.statusCode).toBe(401);
  });

  it('returns 400 for malformed bbox', async () => {
    const app = await buildServer();
    const res = await app.inject({
      method:  'GET',
      url:     '/v1/radar/cameras?bbox=not-valid',
      headers: { Authorization: `Bearer ${fakeJwt('driver-4')}` },
    });
    expect(res.statusCode).toBe(400);
  });

  it('returns cameras array for valid bbox', async () => {
    const mockCameras = [
      { id: 'uuid-1', lat: 37.7749, lng: -122.4194, posted_speed_mph: 35, confirmed_count: 2 },
    ];
    mockFrom.mockReturnValue({
      select: vi.fn().mockReturnValue({
        order: vi.fn().mockReturnValue({
          limit: vi.fn().mockReturnValue({
            gte: vi.fn().mockReturnValue({
              lte: vi.fn().mockReturnValue({
                gte: vi.fn().mockReturnValue({
                  lte: vi.fn().mockResolvedValue({ data: mockCameras, error: null }),
                }),
              }),
            }),
          }),
        }),
      }),
    });

    const app = await buildServer();
    const res = await app.inject({
      method:  'GET',
      url:     '/v1/radar/cameras?bbox=37.70,-122.52,37.84,-122.35',
      headers: { Authorization: `Bearer ${fakeJwt('driver-5')}` },
    });
    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.body) as { cameras: unknown[] };
    expect(Array.isArray(body.cameras)).toBe(true);
  });
});
