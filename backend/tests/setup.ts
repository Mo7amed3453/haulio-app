// Global test setup: set required env vars before any module imports
process.env['SUPABASE_URL'] = 'https://test.supabase.co';
process.env['SUPABASE_SERVICE_KEY'] = 'test-service-key';
process.env['TOMTOM_API_KEY'] = 'test-tomtom-key';
process.env['VALHALLA_LIVE_TRAFFIC_DIR'] = '/tmp/valhalla-test';
process.env['VALHALLA_RELOAD_URL'] = 'http://localhost:8002/reload';
process.env['NODE_ENV'] = 'test';
process.env['PORT'] = '3001';
