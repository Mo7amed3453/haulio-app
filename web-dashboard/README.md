# Haulio Fleet Dashboard – Web

Fleet manager / dispatcher dashboard for real-time traffic events and extreme zones visualization. Built with Vite + React 18 + TypeScript + Leaflet.

## Features

- **Live Map** (`/`) – full-screen Leaflet map with:
  - Layer toggles (Incidents, School Zones, Industrial, Rail Crossings, Historical Corridors, Driver Heatmap, Time Dimension)
  - Supabase Realtime live incident feed sidebar
  - Timeline scrubber (Time Dimension) for 24h zone animation
  - Stats card (active incidents, reroutes, TomTom budget)
- **Zone Editor** (`/zones`) – CRUD for extreme_zones:
  - Draw polygon / marker / polyline with Leaflet-Geoman
  - Multi-day time windows, priority slider
  - Optimistic saves via Supabase

## Quick Start

```bash
# 1. Copy and fill env vars
cp .env.example .env.local

# 2. Install dependencies
pnpm install

# 3. Start dev server
pnpm dev
```

Open [http://localhost:5173](http://localhost:5173).

## Scripts

| Command | Description |
|---------|-------------|
| `pnpm dev` | Start Vite dev server |
| `pnpm build` | TypeCheck + production build to `dist/` |
| `pnpm preview` | Serve the production build locally |
| `pnpm lint` | ESLint check |
| `pnpm typecheck` | TypeScript strict check (no emit) |
| `pnpm test` | Vitest unit tests |
| `pnpm test:watch` | Vitest in watch mode |
| `pnpm test:e2e` | Playwright smoke tests (requires `pnpm build && pnpm preview`) |

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `VITE_SUPABASE_URL` | Yes | Your Supabase project URL |
| `VITE_SUPABASE_ANON_KEY` | Yes | Supabase anon/public key |
| `VITE_TILES_URL` | No | Tile server URL (defaults to OSM) |

## Deploy

### Docker (Hetzner nginx)

```bash
docker build \
  --build-arg VITE_SUPABASE_URL=https://xxx.supabase.co \
  --build-arg VITE_SUPABASE_ANON_KEY=<anon-key> \
  -t haulio-web .

docker run -p 80:80 haulio-web
```

### Vercel (static)

```bash
# From repo root
vercel --cwd web-dashboard
```

Vercel auto-detects Vite. Set `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` in Vercel project settings.

## Architecture

```
src/
├── components/     # UI panels (LayerTogglePanel, IncidentFeed, StatsCard, ZoneEditorForm, TimeDimensionControl)
├── hooks/          # TanStack Query hooks + Supabase Realtime
├── layers/         # Leaflet layer components (one per zone type)
├── lib/            # Supabase client, QueryClient, Zustand store
├── pages/          # LiveMapPage, ZoneEditorPage
├── test/           # Vitest setup
├── types/          # TypeScript interfaces + Leaflet plugin declarations
└── index.css       # Tailwind + Leaflet CSS + custom animations
```

## Supabase Tables Required

| Table | Key Columns |
|-------|-------------|
| `traffic_events` | `id, created_at, type, lat, lng, severity, description, active` |
| `extreme_zones` | `id, name, type, geometry (JSONB), windows (JSONB), priority, created_at` |
| `driver_telemetry` | `id, driver_id, lat, lng, speed, timestamp` |
| `reroute_events` | `id, created_at` |
| `api_usage` | `id, created_at, tomtom_budget_remaining` |

Enable Realtime on `traffic_events` for the live incident feed.
